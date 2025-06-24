package org.jetbrains.kotlin.compiler.plugin.template.ir

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.compiler.plugin.template.fir.MyCodeGenerationExtension
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.primaryConstructorSymbol
import org.jetbrains.kotlin.fir.backend.Fir2IrConverter
import org.jetbrains.kotlin.fir.declarations.origin
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.builders.IrGeneratorContext
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irGetObject
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.declarations.createBlockBody
import org.jetbrains.kotlin.ir.expressions.IrConstKind
import org.jetbrains.kotlin.ir.expressions.IrFunctionAccessExpression
import org.jetbrains.kotlin.ir.expressions.impl.IrCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrConstructorCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrConstImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrGetValueImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrReturnImpl
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrConstructorSymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.IrSymbol
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.irConstructorCall
import org.jetbrains.kotlin.ir.util.properties
import org.jetbrains.kotlin.ir.visitors.IrElementVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.resolve.calls.tower.createFunctionProcessor

internal fun IrGeneratorContext.createIrBuilder(symbol: IrSymbol): DeclarationIrBuilder {
    return DeclarationIrBuilder(this, symbol, symbol.owner.startOffset, symbol.owner.endOffset)
}

class MyCodeIrGenerator(private val pluginContext: IrPluginContext) : IrElementVisitorVoid {
    override fun visitElement(element: IrElement) {
        when (element) {
            is IrDeclaration,
            is IrFile,
            is IrModuleFragment -> element.acceptChildrenVoid(this)

            else -> {}
        }
    }

    override fun visitSimpleFunction(declaration: IrSimpleFunction) {
        val origin = declaration.origin
        if (origin !is IrDeclarationOrigin.GeneratedByPlugin || (origin.pluginKey !is MyCodeGenerationExtension.Key)) return
        require(declaration.body == null)
        val key = origin.pluginKey as MyCodeGenerationExtension.Key
        val annotated = requireNotNull(pluginContext.referenceClass(key.annotated.classId))
        val table = requireNotNull(pluginContext.referenceClass(key.table.classId))
        val constructor = annotated.constructors.first()

        val resultRowClass = declaration.extensionReceiverParameter!!.type.classOrNull!!.owner
        val getFunction = resultRowClass.functions.single {
            it.name.asString() == "get" && it.valueParameters.size == 1 && it.typeParameters.size == 1
        }
        val irBuilder = pluginContext.createIrBuilder(declaration.symbol)
        val constructorCall = irBuilder.irCall(constructor).apply {
            symbol.owner.valueParameters.forEach { parameter ->
                val tableColumnProperty = table.owner.properties.single { it.name == parameter.name }

                println("symbol: $symbol")
                val getCall = irBuilder.irCall(getFunction.symbol).apply {
                    dispatchReceiver =
                        irBuilder.irGet(declaration.extensionReceiverParameter!!) // `this` (the ResultRow)
                    putTypeArgument(0, parameter.type) // The <Type> for `get`

                    // The argument for `get` is the table column (e.g., UsersTable.name)
                    val columnGetterCall = irBuilder.irCall(tableColumnProperty.getter!!.symbol).apply {
                        dispatchReceiver = irBuilder.irGetObject(table.owner.symbol) // `UsersTable` object instance
                    }
                    putValueArgument(0, columnGetterCall)
                }
                putValueArgument(parameter.index, getCall)


            }
        }
        declaration.body = irBuilder.irBlockBody {
            +irReturn(constructorCall)
        }
    }

    private fun findDataClassSymbol(mappings: Map<*, *>): IrClassSymbol? {
        // The first key in mappings should be a property of the data class
        val firstProperty = mappings.keys.firstOrNull() as? IrSimpleFunctionSymbol ?: return null
        return firstProperty.owner.parent as? IrClassSymbol
    }

    private fun findPropertyByName(classSymbol: IrClassSymbol, name: String): IrSimpleFunctionSymbol? {
        return classSymbol.owner.properties.firstOrNull {
            it.name.asString() == name
        }?.getter?.symbol
    }

    private fun findGetFunction(): IrSimpleFunctionSymbol? {
        return pluginContext.referenceFunctions(
            CallableId(
                packageName = FqName("org.jetbrains.kotlin.compiler.plugin.template"),
                className = FqName("ResultRow"),
                callableName = Name.identifier("get")
            )
        ).firstOrNull()
    }
}
