package org.rjava.compiler.targets.c;

import org.rjava.compiler.semantics.representation.RMethod;
import org.rjava.compiler.semantics.representation.RStatement;
import org.rjava.compiler.semantics.representation.RType;
import org.rjava.compiler.semantics.representation.stmt.RAssignStmt;
import org.rjava.compiler.semantics.representation.stmt.RIdentityStmt;
import org.rjava.compiler.semantics.representation.stmt.RInvokeStmt;

import soot.SootMethod;
import soot.jimple.InvokeExpr;
import soot.jimple.internal.JAssignStmt;
import soot.jimple.internal.JIdentityStmt;
import soot.jimple.internal.JSpecialInvokeExpr;
import soot.jimple.internal.JVirtualInvokeExpr;

public class CLanguageIntrinsicGenerator {

    public CLanguageIntrinsicGenerator() {
        // TODO Auto-generated constructor stub
    }

    public void generate(RType type) {
        if (type.getClassName().equals("java.lang.String")) {
            type.setType(null);
            type.setClassName("RJAVA_STR");
            type.setPackageName(null);
            type.setPrimitive(true);
        } else if (type.getClassName().equals("boolean")) {
            type.setType(null);
            type.setClassName("bool");
        }
    }

    public void generate(RStatement stmt) {
        // remove any call to object
        if (stmt instanceof RInvokeStmt) {
            InvokeExpr invoke = stmt.internal().getInvokeExpr();
            if (invoke instanceof JSpecialInvokeExpr && invoke.getMethod().getDeclaringClass().getName().equals("java.lang.Object")) {
                stmt.setIntrinsic(true);
                stmt.setCode(CLanguageGenerator.comment(stmt.toString()));
            } else if (invoke instanceof JVirtualInvokeExpr && invoke.getMethod().getDeclaringClass().getName().equals("java.lang.Object")) {
                stmt.setIntrinsic(true);
                stmt.setCode(CLanguageGenerator.comment(stmt.toString()));
            }
        } 
        // transform char** args into an 'rjava' array
        else if (stmt instanceof RIdentityStmt && stmt.getMethod().isMainMethod() && 
                ((JIdentityStmt)stmt.internal()).getLeftOp().toString().equals("args") 
                && ((JIdentityStmt)stmt.internal()).getRightOp().toString().contains("parameter0")) {
            stmt.setIntrinsic(true);
            stmt.setCode("args = " + CLanguageGenerator.RJAVA_C_ARRAY_TO_RJAVA_ARRAY + "(argc, (long)sizeof(char*), parameter0+sizeof(char*))");
        }
    }

    public void generate(RMethod method) {
        method.setIntrinsic(false);
    }
}
