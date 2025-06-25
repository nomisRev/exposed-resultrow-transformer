package org.jetbrains.kotlin.compiler.plugin.template

import org.jetbrains.exposed.dao.id.CompositeID
import org.jetbrains.exposed.dao.id.CompositeIdTable
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.Alias
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.CompositeColumn
import org.jetbrains.exposed.sql.EntityIDColumnType
import org.jetbrains.exposed.sql.Expression
import org.jetbrains.exposed.sql.ExpressionAlias
import org.jetbrains.exposed.sql.ExpressionWithColumnType
import org.jetbrains.exposed.sql.ExpressionWithColumnTypeAlias
import org.jetbrains.exposed.sql.IColumnType
import org.jetbrains.exposed.sql.IExpressionAlias
import org.jetbrains.exposed.sql.ResultRow.ResultRowCache
import org.jetbrains.exposed.sql.StringColumnType
import org.jetbrains.exposed.sql.exposedLogger
import org.jetbrains.exposed.sql.transactions.TransactionManager
import java.util.HashMap
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.forEach
import kotlin.collections.set

/** A row of data representing a single record retrieved from a database result set. */
public class ResultRow(
    /** Mapping of the expressions stored on this row to their index positions. */
    public val fieldIndex: Map<Expression<*>, Int>,
    private val data: Array<Any?> = arrayOfNulls<Any?>(fieldIndex.size),
) {
    private val lookUpCache = ResultRowCache()

    private fun Column<*>.isEntityIdentifier(): Boolean {
        if (columnType !is EntityIDColumnType<*>) return false

        val tableToCheck = ((table as? Alias<*>)?.delegate ?: table) as? IdTable<*>
        return tableToCheck?.id == (columnType as EntityIDColumnType<*>).idColumn
    }

    /**
     * Retrieves the value of a given expression on this row.
     *
     * @param expression expression to evaluate
     * @throws IllegalStateException if expression is not in record set or if result value is uninitialized
     *
     * @see [getOrNull] to get null in the cases an exception would be thrown
     */
    @Suppress("UNCHECKED_CAST")
    public operator fun <T> get(expression: Expression<T>): T {
        val column = expression as? Column<*>
        return when {
            column?.isEntityIdentifier() == true && column.table is CompositeIdTable -> {
                val table = (column.table as CompositeIdTable)
                val resultID =
                    CompositeID {
                        table.idColumns.forEach { column ->
                            it[column as Column<EntityID<Any>>] = getInternal(column, checkNullability = true).value
                        }
                    }
                EntityID(resultID, table) as T
            }

            else -> getInternal(expression, checkNullability = true)
        }
    }

    private fun <T> hasValue(expression: Expression<T>): Boolean {
        val result = fieldIndex[expression]
        return if (result == null) false else data[result] == NotInitializedValue
    }

    /**
     * Retrieves the value of a given expression on this row.
     * Returns null in the cases an exception would be thrown in [get].
     *
     * @param expression expression to evaluate
     */
    public fun <T> getOrNull(expression: Expression<T>): T? =
        if (hasValue(expression)) getInternal(expression, checkNullability = false) else null

    private fun <T> getInternal(
        expression: Expression<T>,
        checkNullability: Boolean,
    ): T =
        lookUpCache.cached(expression) {
            val rawValue = getRaw(expression)

            if (checkNullability) {
                // && expression.dbDefaultValue != null
                if (rawValue == null && expression is Column<*> && !expression.columnType.nullable) {
                    exposedLogger.warn(
                        "Column ${TransactionManager.current().fullIdentity(expression)} is marked as not null, " +
                            "has default db value, but returns null. Possible have to re-read it from DB.",
                    )
                }
            }

            /*database?.dialect?.let {
                withDialect(it) {
                    rawToColumnValue(rawValue, expression)
                }
            } ?:*/
            rawToColumnValue(rawValue, expression)
        }

    @Suppress("UNCHECKED_CAST")
    private fun <T> rawToColumnValue(
        raw: T?,
        expression: Expression<T>,
    ): T =
        when {
            raw == null -> null
            raw == NotInitializedValue -> error("$expression is not initialized yet")
            expression is ExpressionWithColumnTypeAlias<T> -> rawToColumnValue(raw, expression.delegate)
            expression is ExpressionAlias<T> -> rawToColumnValue(raw, expression.delegate)
            expression is ExpressionWithColumnType<T> ->
                if (expression.columnType is StringColumnType) {
                    raw.toString() as T
                } else {
                    expression.columnType.valueFromDB(raw)
                }
//            expression is Op<Boolean> -> BooleanColumnType.INSTANCE.valueFromDB(raw)
            else -> raw
        } as T

    @Suppress("UNCHECKED_CAST")
    private fun <T> getRaw(expression: Expression<T>): T? {
        if (expression is CompositeColumn<T>) {
            val rawParts = expression.getRealColumns().associateWith { getRaw(it) }
            return expression.restoreValueFromParts(rawParts)
        }

        val index = getExpressionIndex(expression)
        return data[index] as T?
    }

    /**
     * Retrieves the index of a given expression in the [fieldIndex] map.
     *
     * @param expression expression for which to get the index
     * @throws IllegalStateException if expression is not in record set
     */
    private fun <T> getExpressionIndex(expression: Expression<T>): Int =
        fieldIndex[expression]
            ?: fieldIndex.keys
                .firstOrNull { exp ->
                    when (exp) {
                        is Column<*> -> (exp.columnType as? EntityIDColumnType<*>)?.idColumn == expression
                        is IExpressionAlias<*> -> exp.delegate == expression
                        else -> false
                    }
                }?.let { exp -> fieldIndex[exp] }
            ?: error("$expression is not in record set")

    override fun toString(): String = fieldIndex.entries.joinToString { "${it.key}=${data[it.value]}" }

    internal object NotInitializedValue

    public companion object {
        /** Creates a [ResultRow] using the expressions and values provided by [data]. */
        public fun createAndFillValues(vararg data: Pair<Expression<*>, Any?>): ResultRow {
            val fieldIndex = HashMap<Expression<*>, Int>(data.size)
            val values = arrayOfNulls<Any?>(data.size)
            data.forEachIndexed { i, columnAndValue ->
                val (column, value) = columnAndValue
                fieldIndex[column] = i
                values[i] = value
            }
            return ResultRow(fieldIndex, values)
        }
    }

    /**
     * [ResultRowCache] caches the values on reads by `expression`. The value cached by pair of `expression` itself and
     * `columnType` of that expression. It solves the problem of "equal" expression with different column type
     * (like the same column with original type and [EntityIDColumnType])
     */
    private class ResultRowCache {
        private val values: MutableMap<Pair<Expression<*>, IColumnType<*>?>, Any?> = mutableMapOf()

        /**
         * Wrapping function that accept the expression and target function.
         * The function would be called if the value not found in the cache.
         *
         * @param expression is the key of caching
         * @param initializer function that returns the new value if the cache missed
         */
        fun <T> cached(
            expression: Expression<*>,
            initializer: () -> T,
        ): T = values.getOrPut(key(expression), initializer) as T

        /**
         * Remove the value by expression
         *
         * @param expression is the key of caching
         */
        fun remove(expression: Expression<*>) = values.remove(key(expression))

        private fun key(expression: Expression<*>): Pair<Expression<*>, IColumnType<*>?> =
            expression to (expression as? Column<*>)?.columnType
    }
}
