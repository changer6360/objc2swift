/**
 * This file is part of objc2swift.
 * https://github.com/yahoojapan/objc2swift
 *
 * Copyright (c) 2015 Yahoo Japan Corporation
 *
 * For the full copyright and license information, please view the LICENSE
 * file that was distributed with this source code.
 */

package org.objc2swift.converter

import org.antlr.v4.runtime.tree.TerminalNode
import org.objc2swift.converter.ObjCParser._

import scala.collection.JavaConversions._

/**
 * Implements visit methods for declaration contexts.
 */
trait DeclarationVisitor {
  this: ObjC2SwiftBaseConverter with TypeVisitor =>

  import org.objc2swift.converter.util._

  /**
   * Returns translated text of declaration context.
   *
   * @param ctx the parse tree
   **/
  override def visitDeclaration(ctx: DeclarationContext): String = {
    val builder = List.newBuilder[String]
    val specifiers = List.newBuilder[String]
    val ds = ctx.declarationSpecifiers().get

    // prefixes: static, const, etc..
    specifiers += visit(ds)
    val prefixes = specifiers.result().filter(_.nonEmpty)

    // Type
    Option(ds.typeSpecifier()).foreach { ls =>
      if(ls(0).enumSpecifier().nonEmpty) {
        builder += visit(ls(0).enumSpecifier())
      } else {
        ctx.initDeclaratorList() match {
          case Some(c) =>
            // Single declaration with initializer, or list of declarations.
            val currentType = processTypeSpecifierList(ls)
            c.initDeclarator().foreach {
              builder += visitInitDeclarator(_, currentType, prefixes)
            }
          case None =>
            // Short style declaration
            builder += buildShortDeclaration(ls, prefixes).getOrElse("")
        }
      }
    }

    builder.result().filter(_.nonEmpty).mkString("\n") + "\n"
  }

  private def visitInitDeclarator(ctx: InitDeclaratorContext, typeName: String, prefixes: List[String]): String = {
    {
      ctx.declarator().flatMap(_.directDeclarator()).flatMap(_.identifier()).map { _ =>
        buildInitDeclaration(ctx, typeName, prefixes).getOrElse("")
      } orElse
      // not variables declaration? ex) NSLog(foo)
      ctx.declarator().flatMap(_.directDeclarator()).flatMap(_.declarator()).map { s =>
        s"$typeName(${visit(s)})"
      }
    }.getOrElse("")
  }

  /**
   * Returns translated text of short style declaration.
   *
   * Called for single and no initializer declaration. Find id from class_name
   *
   * @param ctxs List of type_specifier contexts.
   * @param prefixes prefix specifiers
   * @return translated text
   */
  private def buildShortDeclaration(ctxs: List[TypeSpecifierContext], prefixes: List[String]): Option[String] = {
    ctxs.last.className().map(visit).filter(_.nonEmpty).map { name =>
      List(
        prefixes.mkString(" "),
        if (prefixes.mkString(" ").split(" ").contains("let")) "" else "var",
        s"$name: ${processTypeSpecifierList(ctxs.init)}").filter(_.nonEmpty).mkString(" ")
    }
  }

  /**
   * Returns translated text of init_declarator context.
   *
   * @param ctx init_declarator context
   * @param tp type name
   * @param prefixes Prefix specifiers
   * @return translated text
   */
  private def buildInitDeclaration(ctx: InitDeclaratorContext, tp: String, prefixes: List[String]): Option[String] = {
    visitChildrenAs(ctx) {
      case TerminalText("=") => "" // NOOP
      case c: DeclaratorContext  => {
        val declarator = visit(c)
        List(
          prefixes.mkString(" "),
          if (
            prefixes.mkString(" ").split(" ").contains("let") ||
            declarator.split(" ").contains("let")) "" else "var",
          s"$declarator: $tp").filter(_.nonEmpty).mkString(" ")
      }
      case c: InitializerContext => s"= ${visit(c)}"
    } match {
      case "" => None
      case s  => Some(s)
    }
  }

  /**
   * Returns translated text of declarator context.
   *
   * @param ctx the parse tree
   **/
  override def visitDeclarator(ctx: DeclaratorContext): String =
    visitChildrenAs(ctx) {
      case c: DirectDeclaratorContext => visit(c)
      case c: PointerContext           => visit(c)
    }

  /**
   * Returns translated text of direct_declarator context.
   *
   * @param ctx the parse tree
   **/
  override def visitDirectDeclarator(ctx: DirectDeclaratorContext): String =
    visitChildrenAs(ctx, "") {
      case TerminalText("(") => "("
      case TerminalText(")") => ")"
      case _: TerminalNode   => "" // NOOP
      case c                 => visit(c)
    }

  /**
   * Returns translated initializer context.
   *
   * @param ctx the parse tree
   **/
  override def visitInitializer(ctx: InitializerContext): String = visitChildren(ctx)

  override def visitTypeVariableDeclarator(ctx: TypeVariableDeclaratorContext): String =
    ctx.declarationSpecifiers().map(_.typeSpecifier()).flatMap { ls =>
      ctx.declarator().flatMap(_.directDeclarator()).flatMap(_.identifier()).map(visit).map(_ + ": " + processTypeSpecifierList(ls))
    }.getOrElse("")

  /**
   * Returns translated text of declaration_specifiers context.
   *
   * @param ctx the parse tree
   **/
  override def visitDeclarationSpecifiers(ctx: DeclarationSpecifiersContext): String =
    visitChildrenAs(ctx) {
      case c: TypeQualifierContext          => visit(c)
      case c: StorageClassSpecifierContext => visit(c)
    }

  /**
   * Returns translated text of type_qualifier context.
   *
   * [Supported qualifier]
   * - const
   *
   * @param ctx the parse tree
   **/
  override def visitTypeQualifier(ctx: TypeQualifierContext): String =
    visitChildrenAs(ctx) {
      case TerminalText("const") => "let"
    }

  /**
   * Returns translated text of storage_class_specifier context.
   *
   * [Supported specifier]
   * - static
   *
   * @param ctx the parse tree
   **/
  override def visitStorageClassSpecifier(ctx: StorageClassSpecifierContext): String =
    visitChildrenAs(ctx) {
      case TerminalText("static") => "static"
    }
}
