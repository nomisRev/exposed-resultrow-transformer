package org.jetbrains.exposed.compiler.plugin.ir

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.exposed.compiler.plugin.Module
import org.jetbrains.exposed.compiler.plugin.fir.MyCodeGenerationExtension
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.builders.IrGeneratorContext
import org.jetbrains.kotlin.ir.builders.declarations.addValueParameter
import org.jetbrains.kotlin.ir.builders.declarations.buildFun
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irGetObject
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrFactory
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.expressions.impl.IrFunctionExpressionImpl
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrConstructorSymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.IrSymbol
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.properties
import org.jetbrains.kotlin.ir.visitors.IrVisitor
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

class MyCodeIrGenerationExtension(
    private val module: Module,
) : IrGenerationExtension {
    override fun generate(
        moduleFragment: IrModuleFragment,
        pluginContext: IrPluginContext,
    ) {
        moduleFragment.accept(MyCodeIrGenerator(pluginContext, module), null)
    }
}

internal fun IrGeneratorContext.createIrBuilder(symbol: IrSymbol): DeclarationIrBuilder =
    DeclarationIrBuilder(this, symbol, symbol.owner.startOffset, symbol.owner.endOffset)

class MyCodeIrGenerator(
    private val pluginContext: IrPluginContext,
    private val module: Module,
) : IrVisitor<Unit, Nothing?>() {
    override fun visitElement(
        element: IrElement,
        data: Nothing?,
    ) {
        element.acceptChildren(this, data)
    }

    override fun visitSimpleFunction(
        declaration: IrSimpleFunction,
        data: Nothing?,
    ) {
        val keyOrNull =
            (declaration.origin as? IrDeclarationOrigin.GeneratedByPlugin)?.pluginKey as? MyCodeGenerationExtension.Key
                ?: return

        val extensionReceiverParameter = declaration.extensionReceiverParameter
        val annotated = pluginContext.referenceClass(keyOrNull.annotated.classId)
        val table = pluginContext.referenceClass(keyOrNull.tableClassId)
        val constructor = annotated?.constructors?.firstOrNull()

        @Suppress("ComplexCondition")
        if (
            declaration.body == null &&
            extensionReceiverParameter != null &&
            annotated != null &&
            table != null &&
            constructor != null
        ) {
            when (keyOrNull) {
                is MyCodeGenerationExtension.SingleKey ->
                    extracted(
                        declaration,
                        keyOrNull,
                        extensionReceiverParameter,
                        table,
                        constructor,
                    )

                is MyCodeGenerationExtension.IterableKey ->
                    extracted(
                        declaration,
                        annotated,
                        extensionReceiverParameter,
                        table,
                    )
            }
        }
    }

    val resultRowClass = requireNotNull(pluginContext.referenceClass(module.classIds.resultRow))
    val getFunction =
        resultRowClass.functions
            .single {
                it.owner.name.asString() == "get" &&
                    it.owner.valueParameters.size == 1 &&
                    it.owner.typeParameters.size == 1
            }.owner

    val mapFunction: IrSimpleFunctionSymbol =
        requireNotNull(
            pluginContext
                .referenceFunctions(CallableId(FqName("kotlin.collections"), Name.identifier("map")))
                .firstOrNull {
                    it.owner.extensionReceiverParameter
                        ?.symbol
                        ?.owner
                        ?.type
                        ?.classOrNull ==
                        pluginContext.irBuiltIns.iterableClass
                },
        )

    @Suppress("NestedBlockDepth")
    private fun extracted(
        declaration: IrSimpleFunction,
        key: MyCodeGenerationExtension.SingleKey,
        extensionReceiverParameter: IrValueParameter,
        table: IrClassSymbol,
        constructor: IrConstructorSymbol,
    ) {
        module.logger.log { "Generation IR function for ${declaration.name.asString()}. $resultRowClass, $table" }
        val irBuilder = pluginContext.createIrBuilder(declaration.symbol)

        val constructorCall =
            irBuilder.irCall(constructor).apply {
                symbol.owner.valueParameters.forEach { parameter ->
                    val columnGet = table.columnGet(parameter)
                    if (columnGet == null) {
                        pluginContext.messageCollector
                            .report(
                                CompilerMessageSeverity.ERROR,
                                "Column getter not found in ${key.tableClassId} during IR lowering for parameter" +
                                    " ${parameter.name.asString()} of ${key.annotated.classId}",
                            )
                        return
                    }

                    // this.get<Type>(TableName.columnGet) // this: ResultRow
                    val getCall =
                        irBuilder.irCall(getFunction.symbol).apply {
                            dispatchReceiver = irBuilder.irGet(extensionReceiverParameter)
                            putTypeArgument(0, parameter.type)
                            putValueArgument(
                                0,
                                irBuilder.irCall(columnGet.symbol).apply {
                                    dispatchReceiver = irBuilder.irGetObject(table.owner.symbol)
                                },
                            )
                        }
                    putValueArgument(parameter.index, getCall)
                }
            }
        declaration.body =
            irBuilder.irBlockBody {
                +irReturn(constructorCall)
            }
    }

    private fun IrClassSymbol.columnGet(parameter: IrValueParameter): IrSimpleFunction? =
        owner.properties.singleOrNull { it.name == parameter.name }?.getter

    private fun extracted(
        declaration: IrSimpleFunction,
        annotated: IrClassSymbol,
        extensionReceiverParameter: IrValueParameter,
        table: IrClassSymbol,
    ) {
        val toUserFunction = findToUserFunction(declaration) ?: return

        module.logger.log { "Generation IR function for ${declaration.name.asString()}. $resultRowClass, $table" }
        val irBuilder = pluginContext.createIrBuilder(declaration.symbol)

        // { it: ResultRow ->
        val lambda =
            pluginContext.irFactory
                .lambda(annotated.defaultType)
                .apply {
                    parent = declaration
                    addValueParameter("it", resultRowClass.defaultType)

                    val lambdaBuilder = pluginContext.createIrBuilder(this.symbol)
                    body =
                        lambdaBuilder.irBlockBody {
                            val toUserCall =
                                lambdaBuilder.irCall(toUserFunction.symbol).apply {
                                    extensionReceiver = lambdaBuilder.irGet(valueParameters[0])
                                }
                            +irReturn(toUserCall)
                        }
                }.functionExpression(annotated)

        // Iterable<ResultRow>.map<ResultRow, ResultType>(lambda)
        val mapCall =
            irBuilder.irCall(mapFunction).apply {
                extensionReceiver = irBuilder.irGet(extensionReceiverParameter)
                putTypeArgument(0, resultRowClass.defaultType)
                putTypeArgument(1, annotated.defaultType)
                putValueArgument(0, lambda)
            }
        declaration.body =
            irBuilder.irBlockBody {
                +irReturn(mapCall)
            }
    }

    private fun IrSimpleFunction.functionExpression(annotated: IrClassSymbol): IrFunctionExpressionImpl =
        IrFunctionExpressionImpl(
            UNDEFINED_OFFSET,
            UNDEFINED_OFFSET,
            pluginContext.irBuiltIns.functionN(1).typeWith(resultRowClass.defaultType, annotated.defaultType),
            this,
            IrStatementOrigin.LAMBDA,
        )

    private fun findToUserFunction(declaration: IrSimpleFunction): IrSimpleFunction? {
        val fileDeclarations =
            (declaration.parent as? IrFile)?.declarations ?: (declaration.parent as IrClass).declarations
        return fileDeclarations
            .filterIsInstance<IrSimpleFunction>()
            .firstOrNull {
                it.name.asString() == "toUser" && it.extensionReceiverParameter?.type?.classOrNull == resultRowClass
            }
    }
}

fun IrFactory.lambda(returnType: IrType) =
    buildFun {
        startOffset = UNDEFINED_OFFSET
        endOffset = UNDEFINED_OFFSET
        origin = IrDeclarationOrigin.LOCAL_FUNCTION_FOR_LAMBDA
        name = Name.special("<anonymous>")
        visibility = DescriptorVisibilities.LOCAL
        modality = Modality.FINAL
        isSuspend = false
        isInline = false
        isExpect = false
        this.returnType = returnType
        isTailrec = false
        isOperator = false
        isInfix = false
    }
