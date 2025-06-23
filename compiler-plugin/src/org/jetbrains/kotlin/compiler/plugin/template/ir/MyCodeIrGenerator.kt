package org.jetbrains.kotlin.compiler.plugin.template.ir

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.compiler.plugin.template.fir.MyCodeGenerationExtension
import org.jetbrains.kotlin.fir.declarations.origin
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.createBlockBody
import org.jetbrains.kotlin.ir.expressions.IrConstKind
import org.jetbrains.kotlin.ir.expressions.impl.IrConstImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrReturnImpl
import org.jetbrains.kotlin.ir.visitors.IrElementVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid

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
        (origin.pluginKey as MyCodeGenerationExtension.Key).mappings.forEach { (symbol, column) ->

        }
        val const = IrConstImpl(-1, -1, pluginContext.irBuiltIns.stringType, IrConstKind.String, value = "OK")
        val returnStatement = IrReturnImpl(-1, -1, pluginContext.irBuiltIns.nothingType, declaration.symbol, const)
        declaration.body = pluginContext.irFactory.createBlockBody(-1, -1, listOf(returnStatement))
    }
}
