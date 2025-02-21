package org.apache.spark.sql.rf

import org.apache.spark.sql.catalyst.expressions.codegen.Block._
import org.apache.spark.sql.catalyst.InternalRow
import org.apache.spark.sql.catalyst.expressions.Expression
import org.apache.spark.sql.catalyst.expressions.codegen.{CodeGenerator, CodegenContext, ExprCode, FalseLiteral}

/**
 * An expression with five inputs and one output. The output is by default evaluated to null if any input is evaluated to null
 */
abstract class QuinaryExpression extends Expression {

  override def foldable: Boolean = children.forall(_.foldable)

  override def nullable: Boolean = children.exists(_.nullable)

  /**
   * Default behavior of evaluation according to the default nullability of QuaternaryExpression.
   * If subclass of QuaternaryExpression override nullable, probably should also override this.
   */
  override def eval(input: InternalRow): Any = {
    val exprs = children
    val value1 = exprs(0).eval(input)
    if (value1 != null) {
      val value2 = exprs(1).eval(input)
      if (value2 != null) {
        val value3 = exprs(2).eval(input)
        if (value3 != null) {
          val value4 = exprs(3).eval(input)
          if (value4 != null) {
            val value5 = exprs(4).eval(input)
            if (value5 != null) {
              return nullSafeEval(value1, value2, value3, value4, value5)
            }
          }
        }
      }
    }
    null
  }

  /**
   * Called by default [[eval]] implementation. If subclass of QuinaryExpression keep the
   *  default nullability, they can override this method to save null-check code.  If we need
   *  full control of evaluation process, we should override [[eval]].
   */
  protected def nullSafeEval(input1: Any, input2: Any, input3: Any, input4: Any, input5: Any): Any =
    sys.error(s"QuinaryExpressions must override either eval or nullSafeEval")

  /**
   * Short hand for generating quinary evaluation code.
   * If either of the sub-expressions is null, the result of this computation
   * is assumed to be null.
   *
   * @param f accepts five variable names and returns Java code to compute the output.
   */
  protected def defineCodeGen(ctx: CodegenContext, ev: ExprCode, f: (String, String, String, String, String) => String): ExprCode = {
    nullSafeCodeGen(ctx, ev, (eval1, eval2, eval3, eval4, eval5) => {
      s"${ev.value} = ${f(eval1, eval2, eval3, eval4, eval5)};"
    })
  }

  /**
   * Short hand for generating quinary evaluation code.
   * If either of the sub-expressions is null, the result of this computation
   * is assumed to be null.
   *
   * @param f function that accepts the 5 non-null evaluation result names of children
   *          and returns Java code to compute the output.
   */
  protected def nullSafeCodeGen(ctx: CodegenContext, ev: ExprCode, f: (String, String, String, String, String) => String): ExprCode = {
    val firstGen = children(0).genCode(ctx)
    val secondGen = children(1).genCode(ctx)
    val thridGen = children(2).genCode(ctx)
    val fourthGen = children(3).genCode(ctx)
    val fifthGen = children(4).genCode(ctx)
    val resultCode = f(firstGen.value, secondGen.value, thridGen.value, fourthGen.value, fifthGen.value)

    if (nullable) {
      val nullSafeEval =
        firstGen.code + ctx.nullSafeExec(children(0).nullable, firstGen.isNull) {
          secondGen.code + ctx.nullSafeExec(children(1).nullable, secondGen.isNull) {
            thridGen.code + ctx.nullSafeExec(children(2).nullable, thridGen.isNull) {
              fourthGen.code + ctx.nullSafeExec(children(3).nullable, fourthGen.isNull) {
                fifthGen.code + ctx.nullSafeExec(children(4).nullable, fifthGen.isNull) {
                  s"""
                  ${ev.isNull} = false; // resultCode could change nullability.
                  $resultCode
                """
                }
              }
            }
          }
        }

      ev.copy(code = code"""
        boolean ${ev.isNull} = true;
        ${CodeGenerator.javaType(dataType)} ${ev.value} = ${CodeGenerator.defaultValue(dataType)};
        $nullSafeEval""")
    } else {
      ev.copy(code = code"""
        ${firstGen.code}
        ${secondGen.code}
        ${thridGen.code}
        ${fourthGen.code}
        ${fifthGen.code}
        ${CodeGenerator.javaType(dataType)} ${ev.value} = ${CodeGenerator.defaultValue(dataType)};
        $resultCode""", isNull = FalseLiteral)
    }
  }
}
