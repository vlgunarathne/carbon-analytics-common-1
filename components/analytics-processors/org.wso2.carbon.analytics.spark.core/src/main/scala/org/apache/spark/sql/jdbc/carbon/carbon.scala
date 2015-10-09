/*
 * Copyright (c) 2015, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.spark.sql.jdbc

import java.sql.{Connection, PreparedStatement}

import org.apache.spark.Logging
import org.apache.spark.sql.types._
import org.apache.spark.sql.{DataFrame, Row}
import org.wso2.carbon.analytics.spark.core.exception.AnalyticsExecutionException
import org.wso2.carbon.analytics.spark.core.sources.AnalyticsDatasourceWrapper

package object carbon {

  object JDBCWriteDetails extends Logging {
    /**
     * Compute the schema string for this RDD.
     */
    def schemaString(df: DataFrame, url: String): String = {
      val sb = new StringBuilder()
      val dialect = JdbcDialects.get(url)
      df.schema.fields foreach { field => {
        val name = field.name
        val typ: String =
          dialect.getJDBCType(field.dataType).map(_.databaseTypeDefinition).getOrElse(
            field.dataType match {
              case IntegerType => "INTEGER"
              case LongType => "BIGINT"
              case DoubleType => "DOUBLE PRECISION"
              case FloatType => "REAL"
              case ShortType => "INTEGER"
              case ByteType => "BYTE"
              case BooleanType => "BIT(1)"
              case StringType => "TEXT"
              case BinaryType => "BLOB"
              case TimestampType => "TIMESTAMP"
              case DateType => "DATE"
              case DecimalType.Unlimited => "DECIMAL(40,20)"
              case _ => throw new IllegalArgumentException(s"Don't know how to save $field to JDBC")
            })
        val nullable = if (field.nullable) {
          ""
        } else {
          "NOT NULL"
        }
        sb.append(s", $name $typ $nullable")
      }
      }
      if (sb.length < 2) {
        ""
      } else {
        sb.substring(2)
      }
    }

    /**
     * Saves the RDD to the database in a single transaction.
     */
    def saveTable(
                   df: DataFrame,
                   dataSource: String,
                   tableName: String) {
      val rddSchema = df.schema
      val dsWrapper = new AnalyticsDatasourceWrapper(dataSource)

      try {
        val conn = dsWrapper.getConnection
        try {
          val dialect = JdbcDialects.get(conn.getMetaData.getURL)
          val nullTypes: Array[Int] = df.schema.fields.map { field =>
            dialect.getJDBCType(field.dataType).map(_.jdbcNullType).getOrElse(
              field.dataType match {
                case IntegerType => java.sql.Types.INTEGER
                case LongType => java.sql.Types.BIGINT
                case DoubleType => java.sql.Types.DOUBLE
                case FloatType => java.sql.Types.REAL
                case ShortType => java.sql.Types.INTEGER
                case ByteType => java.sql.Types.INTEGER
                case BooleanType => java.sql.Types.BIT
                case StringType => java.sql.Types.CLOB
                case BinaryType => java.sql.Types.BLOB
                case TimestampType => java.sql.Types.TIMESTAMP
                case DateType => java.sql.Types.DATE
                case DecimalType.Fixed(precision, scale) => java.sql.Types.NUMERIC
                case DecimalType.Unlimited => java.sql.Types.NUMERIC
                case _ => throw new IllegalArgumentException(
                  s"Can't translate null value for field $field")
              })
                                                           }
          df.foreachPartition { iterator =>
            JDBCWriteDetails.savePartition(dsWrapper.getConnection, tableName, iterator, rddSchema, nullTypes)
                              }
        } finally {
          conn.close()
        }
      }
      catch {
        case e: Exception =>
          throw new AnalyticsExecutionException ("Error while saving data to the table "
                                                 + tableName + " : " + e.getMessage , e)
      }
    }

    /**
     * Saves a partition of a DataFrame to the JDBC database.  This is done in
     * a single database transaction in order to avoid repeatedly inserting
     * data as much as possible.
     *
     * It is still theoretically possible for rows in a DataFrame to be
     * inserted into the database more than once if a stage somehow fails after
     * the commit occurs but before the stage can return successfully.
     *
     * This is not a closure inside saveTable() because apparently cosmetic
     * implementation changes elsewhere might easily render such a closure
     * non-java.io.Serializable.  Instead, we explicitly close over all variables that
     * are used.
     */
    def savePartition(
                       getConnection: () => Connection,
                       table: String,
                       iterator: Iterator[Row],
                       rddSchema: StructType,
                       nullTypes: Array[Int]): Iterator[Byte] = {
      val conn = getConnection()
      var committed = false
      try {
        conn.setAutoCommit(false) // Everything in the same db transaction.
        val stmt = insertStatement(conn, table, rddSchema)
        try {
          while (iterator.hasNext) {
            val row = iterator.next()
            val numFields = rddSchema.fields.length
            var i = 0
            while (i < numFields) {
              if (row.isNullAt(i)) {
                stmt.setNull(i + 1, nullTypes(i))
              } else {
                rddSchema.fields(i).dataType match {
                  case IntegerType => stmt.setInt(i + 1, row.getInt(i))
                  case LongType => stmt.setLong(i + 1, row.getLong(i))
                  case DoubleType => stmt.setDouble(i + 1, row.getDouble(i))
                  case FloatType => stmt.setFloat(i + 1, row.getFloat(i))
                  case ShortType => stmt.setInt(i + 1, row.getShort(i))
                  case ByteType => stmt.setInt(i + 1, row.getByte(i))
                  case BooleanType => stmt.setBoolean(i + 1, row.getBoolean(i))
                  case StringType => stmt.setString(i + 1, row.getString(i))
                  case BinaryType => stmt.setBytes(i + 1, row.getAs[Array[Byte]](i))
                  case TimestampType => stmt.setTimestamp(i + 1, row.getAs[java.sql.Timestamp](i))
                  case DateType => stmt.setDate(i + 1, row.getAs[java.sql.Date](i))
                  case DecimalType.Fixed(precision, scale) => stmt.setBigDecimal(i + 1,
                    row.getAs[java.math.BigDecimal](i))
                  case DecimalType.Unlimited => stmt.setBigDecimal(i + 1,
                    row.getAs[java.math.BigDecimal](i))
                  case _ => throw new IllegalArgumentException(
                    s"Can't translate non-null value for field $i")
                }
              }
              i = i + 1
            }
            stmt.executeUpdate()
          }
        } finally {
          stmt.close()
        }
        conn.commit()
        committed = true
      } finally {
        if (!committed) {
          // The stage must fail.  We got here through an exception path, so
          // let the exception through unless rollback() or close() want to
          // tell the user about another problem.
          conn.rollback()
          conn.close()
        } else {
          // The stage must succeed.  We cannot propagate any exception close() might throw.
          try {
            conn.close()
          } catch {
            case e: Exception => logWarning("Transaction succeeded, but closing failed", e)
          }
        }
      }
      Array[Byte]().iterator
    }

    /**
     * Returns a PreparedStatement that inserts a row into table via conn.
     */
    def insertStatement(conn: Connection, table: String, rddSchema: StructType):
    PreparedStatement = {
      val sql = new StringBuilder(s"INSERT INTO $table VALUES (")
      var fieldsLeft = rddSchema.fields.length
      while (fieldsLeft > 0) {
        sql.append("?")
        if (fieldsLeft > 1) {
          sql.append(", ")
        } else {
          sql.append(")")
        }
        fieldsLeft = fieldsLeft - 1
      }
      conn.prepareStatement(sql.toString())
    }

  }

}