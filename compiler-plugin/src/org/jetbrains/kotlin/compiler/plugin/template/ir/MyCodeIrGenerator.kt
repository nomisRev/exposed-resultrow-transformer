package org.jetbrains.kotlin.compiler.plugin.template.ir

import org.jetbrains.kotlin.GeneratedDeclarationKey
import org.jetbrains.kotlin.analysis.decompiler.stub.flags.VISIBILITY
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.ir.createExtensionReceiver
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.cfg.pseudocode.or
import org.jetbrains.kotlin.compiler.plugin.template.Module
import org.jetbrains.kotlin.compiler.plugin.template.fir.MyCodeGenerationExtension
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.builders.IrBlockBodyBuilder
import org.jetbrains.kotlin.ir.builders.IrGeneratorContext
import org.jetbrains.kotlin.ir.builders.declarations.addValueParameter
import org.jetbrains.kotlin.ir.builders.declarations.buildFun
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irGetObject
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrDeclarationParent
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.IrFunctionExpression
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.expressions.impl.IrExpressionBodyImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrFunctionExpressionImpl
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.IrSymbol
import org.jetbrains.kotlin.ir.symbols.impl.IrSimpleFunctionSymbolImpl
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.SYNTHETIC_OFFSET
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.isTypeParameter
import org.jetbrains.kotlin.ir.util.properties
import org.jetbrains.kotlin.ir.visitors.IrVisitor
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

class MyCodeIrGenerationExtension(private val module: Module) : IrGenerationExtension {
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        moduleFragment.accept(MyCodeIrGenerator(pluginContext, module), null)
    }
}

internal fun IrGeneratorContext.createIrBuilder(symbol: IrSymbol): DeclarationIrBuilder {
    return DeclarationIrBuilder(this, symbol, symbol.owner.startOffset, symbol.owner.endOffset)
}

class MyCodeIrGenerator(private val pluginContext: IrPluginContext, private val module: Module) :
    IrVisitor<Unit, Nothing?>() {
    override fun visitElement(element: IrElement, data: Nothing?) {
        element.acceptChildren(this, data)
    }

    override fun visitSimpleFunction(declaration: IrSimpleFunction, data: Nothing?) {
        val keyOrNull =
            (declaration.origin as? IrDeclarationOrigin.GeneratedByPlugin)?.pluginKey as? MyCodeGenerationExtension.Key
        when (keyOrNull) {
            is MyCodeGenerationExtension.SingleKey -> extracted(declaration, keyOrNull)
            is MyCodeGenerationExtension.IterableKey -> extracted(declaration, keyOrNull)
            null -> return
        }
    }

    val resultRowClass = requireNotNull(pluginContext.referenceClass(module.classIds.resultRow))
    val getFunction = resultRowClass.functions.single {
        it.owner.name.asString() == "get" && it.owner.valueParameters.size == 1 && it.owner.typeParameters.size == 1
    }.owner

    private fun extracted(
        declaration: IrSimpleFunction,
        pluginKey: MyCodeGenerationExtension.SingleKey
    ) {
        require(declaration.body == null)
        val key = pluginKey as MyCodeGenerationExtension.Key
        val annotated = requireNotNull(pluginContext.referenceClass(key.annotated.classId))
        val table: IrClassSymbol = requireNotNull(pluginContext.referenceClass(key.tableClassId))
        val constructor = annotated.constructors.first()

        module.logger.log { "Generation IR function for ${declaration.name.asString()}. $resultRowClass, $table, $constructor" }
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

    private fun extracted(
        declaration: IrSimpleFunction,
        pluginKey: MyCodeGenerationExtension.IterableKey
    ) {
        require(declaration.body == null)
        val key = pluginKey as MyCodeGenerationExtension.Key
        val annotated = requireNotNull(pluginContext.referenceClass(key.annotated.classId))
        val table: IrClassSymbol = requireNotNull(pluginContext.referenceClass(key.tableClassId))
        val constructor = annotated.constructors.firstOrNull() ?: return

        module.logger.log { "Generation IR function for ${declaration.name.asString()}. $resultRowClass, $table, $constructor" }
        val irBuilder = pluginContext.createIrBuilder(declaration.symbol)

        val mapFunction: IrSimpleFunctionSymbol =
            pluginContext.referenceFunctions(CallableId(FqName("kotlin.collections"), Name.identifier("map")))
                .firstOrNull { it.owner.extensionReceiverParameter?.symbol?.owner?.type?.classOrNull == pluginContext.irBuiltIns.iterableClass }
                ?: return

        // Find the single toUser function that we should have generated
        val fileDeclarations =
            (declaration.parent as? IrFile)?.declarations ?: (declaration.parent as IrClass).declarations
        val toUserFunction = fileDeclarations
            .filterIsInstance<IrSimpleFunction>()
            .find { it.name.asString() == "toUser" && it.extensionReceiverParameter?.type?.classOrNull == resultRowClass }
            ?: return

        // Create a simple call to map with the existing toUser function
        // For now, let's use a different approach - create a lambda manually that calls toUser
        val lambda = pluginContext.irFactory.buildFun {
            startOffset = UNDEFINED_OFFSET
            endOffset = UNDEFINED_OFFSET
            origin = IrDeclarationOrigin.LOCAL_FUNCTION_FOR_LAMBDA
            name = Name.special("<anonymous>")
            visibility = DescriptorVisibilities.LOCAL
            modality = declaration.modality
            isSuspend = false
            isInline = false
            isExpect = false
            returnType = annotated.defaultType
            isTailrec = false
            isOperator = false
            isInfix = false
        }.apply {
            parent = declaration
            addValueParameter("it", resultRowClass.defaultType)

            val lambdaBuilder = pluginContext.createIrBuilder(this.symbol)
            body = lambdaBuilder.irBlockBody {
                val itParam = valueParameters[0]
                val toUserCall = lambdaBuilder.irCall(toUserFunction.symbol).apply {
                    extensionReceiver = lambdaBuilder.irGet(itParam)
                }
                +irReturn(toUserCall)
            }
        }

        val functionReference = IrFunctionExpressionImpl(
            UNDEFINED_OFFSET, UNDEFINED_OFFSET,
            pluginContext.irBuiltIns.functionN(1).typeWith(resultRowClass.defaultType, annotated.defaultType),
            lambda,
            IrStatementOrigin.LAMBDA
        )

        val mapCall = irBuilder.irCall(mapFunction).apply {
            // Set the type arguments for map<T, R>
            putTypeArgument(0, resultRowClass.defaultType) // T (input type)
            putTypeArgument(1, annotated.defaultType)      // R (output type)

            // Set the receiver (the collection being mapped)
            extensionReceiver = irBuilder.irGet(declaration.extensionReceiverParameter!!)

            // Set the function reference as the value argument
            putValueArgument(0, functionReference)
        }
        declaration.body = irBuilder.irBlockBody {
            +irReturn(mapCall)
        }
    }
}
