package org.jetbrains.kotlin.compiler.plugin.template.ir

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.compiler.plugin.template.fir.MyCodeGenerationExtension
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.builders.IrGeneratorContext
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irGetObject
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.symbols.IrSymbol
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.properties
import org.jetbrains.kotlin.ir.visitors.IrVisitor

class MyCodeIrGenerationExtension : IrGenerationExtension {
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        moduleFragment.accept(MyCodeIrGenerator(pluginContext), null)
    }
}

internal fun IrGeneratorContext.createIrBuilder(symbol: IrSymbol): DeclarationIrBuilder {
    return DeclarationIrBuilder(this, symbol, symbol.owner.startOffset, symbol.owner.endOffset)
}

class MyCodeIrGenerator(private val pluginContext: IrPluginContext) : IrVisitor<Unit, Nothing?>() {
    override fun visitElement(element: IrElement, data: Nothing?) {
        element.acceptChildren(this, data)
    }

    override fun visitSimpleFunction(declaration: IrSimpleFunction, data: Nothing?) {
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
}
