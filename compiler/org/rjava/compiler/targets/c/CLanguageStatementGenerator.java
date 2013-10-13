package org.rjava.compiler.targets.c;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.rjava.compiler.RJavaCompiler;
import org.rjava.compiler.exception.RJavaError;
import org.rjava.compiler.pass.PointsToAnalysisPass;
import org.rjava.compiler.semantics.SemanticMap;
import org.rjava.compiler.semantics.representation.RClass;
import org.rjava.compiler.semantics.representation.RField;
import org.rjava.compiler.semantics.representation.RLocal;
import org.rjava.compiler.semantics.representation.RMethod;
import org.rjava.compiler.semantics.representation.RStatement;
import org.rjava.compiler.semantics.representation.RType;
import org.rjava.compiler.semantics.representation.stmt.*;
import org.rjava.compiler.targets.c.runtime.CLanguageRuntime;
import org.rjava.compiler.targets.c.runtime.RuntimeHelpers;
import org.rjava.compiler.util.Statistics;

import soot.Local;
import soot.PointsToAnalysis;
import soot.PointsToSet;
import soot.Scene;
import soot.SootClass;
import soot.Type;
import soot.Unit;
import soot.Value;
import soot.jimple.BinopExpr;
import soot.jimple.ConditionExpr;
import soot.jimple.InvokeExpr;
import soot.jimple.NullConstant;
import soot.jimple.NumericConstant;
import soot.jimple.ParameterRef;
import soot.jimple.StaticFieldRef;
import soot.jimple.StringConstant;
import soot.jimple.internal.AbstractStmt;
import soot.jimple.internal.JArrayRef;
import soot.jimple.internal.JAssignStmt;
import soot.jimple.internal.JCastExpr;
import soot.jimple.internal.JCmpExpr;
import soot.jimple.internal.JCmpgExpr;
import soot.jimple.internal.JCmplExpr;
import soot.jimple.internal.JEnterMonitorStmt;
import soot.jimple.internal.JEqExpr;
import soot.jimple.internal.JExitMonitorStmt;
import soot.jimple.internal.JGotoStmt;
import soot.jimple.internal.JIdentityStmt;
import soot.jimple.internal.JIfStmt;
import soot.jimple.internal.JInstanceFieldRef;
import soot.jimple.internal.JInstanceOfExpr;
import soot.jimple.internal.JInterfaceInvokeExpr;
import soot.jimple.internal.JInvokeStmt;
import soot.jimple.internal.JLengthExpr;
import soot.jimple.internal.JLookupSwitchStmt;
import soot.jimple.internal.JNeExpr;
import soot.jimple.internal.JNegExpr;
import soot.jimple.internal.JNewArrayExpr;
import soot.jimple.internal.JNewExpr;
import soot.jimple.internal.JNewMultiArrayExpr;
import soot.jimple.internal.JNopStmt;
import soot.jimple.internal.JSpecialInvokeExpr;
import soot.jimple.internal.JStaticInvokeExpr;
import soot.jimple.internal.JTableSwitchStmt;
import soot.jimple.internal.JUshrExpr;
import soot.jimple.internal.JVirtualInvokeExpr;
import soot.jimple.internal.JimpleLocal;

public class CLanguageStatementGenerator {
    CLanguageNameGenerator name;
    CLanguageGenerator generator;
    
    private int labelIndex = 0;
    // <target.hashCode(), labelIndex>
    private Map<Integer, Integer> jumpLabels = new HashMap<Integer, Integer>();
    
    public CLanguageStatementGenerator(CLanguageGenerator generator) {
        this.generator = generator;
        name = new CLanguageNameGenerator(generator);
    }
    
    /*
     * get from RLocal
     */
    public String get(RLocal local) {
        String ret = "";
        RType localType = local.getType();
        ret += name.get(localType);
        // array to pointer
        if (localType.isArray())
            ret += CLanguageGenerator.POINTER;
        if (!localType.isPrimitive() && !localType.isVoidType() && !local.isByValue())
            ret += CLanguageGenerator.POINTER;
        ret += " " + local.getName();
        return ret;
    }
    
    /*
     * get from RStatement
     */
    public String get(RStatement stmt) throws RJavaError {
        switch (stmt.getType()) {
            case RStatement.ASSIGN_STMT:            return destLabel(stmt) + get((RAssignStmt) stmt);
            case RStatement.BREAKPOINT_STMT:        return destLabel(stmt) + get((RBreakpointStmt) stmt);
            case RStatement.ENTER_MONITOR_STMT:     return destLabel(stmt) + get((REnterMonitorStmt) stmt);
            case RStatement.EXIT_MONITOR_STMT:      return destLabel(stmt) + get((RExitMonitorStmt) stmt);
            case RStatement.GOTO_STMT:              return destLabel(stmt) + get((RGotoStmt) stmt);
            case RStatement.IDENTITY_STMT:          return destLabel(stmt) + get((RIdentityStmt) stmt);
            case RStatement.IF_STMT:                return destLabel(stmt) + get((RIfStmt) stmt);
            case RStatement.INVOKE_STMT:            return destLabel(stmt) + get((RInvokeStmt) stmt);
            case RStatement.LOOKUP_SWITCH_STMT:     return destLabel(stmt) + get((RLookupSwitchStmt) stmt);
            case RStatement.NOP_STMT:               return destLabel(stmt) + get((RNopStmt) stmt);
            case RStatement.RET_STMT:               return destLabel(stmt) + get((RRetStmt) stmt);
            case RStatement.RETURN_STMT:            return destLabel(stmt) + get((RReturnStmt) stmt);
            case RStatement.RETURN_VOID_STMT:       return destLabel(stmt) + get((RReturnVoidStmt) stmt);
            case RStatement.TABLE_SWITCH_STMT:      return destLabel(stmt) + get((RTableSwitchStmt) stmt);
            case RStatement.THROW_STMT:             return destLabel(stmt) + get((RThrowStmt) stmt);
            default: return null;
        }
    }
    
    private boolean isValidSootLValue(Value v) {
        return v instanceof JimpleLocal || v instanceof JInstanceFieldRef || v instanceof StaticFieldRef || v instanceof JArrayRef;
    }
    
    private String fromSootLValue(Value leftOp) {
        // left op -> local | field | local.field | local[imm]
        String leftOpStr = CLanguageGenerator.INCOMPLETE_IMPLEMENTATION;
        if (leftOp instanceof soot.jimple.internal.JimpleLocal) {
            leftOpStr = name.fromSootLocal((Local) leftOp);
        } else if (leftOp instanceof soot.jimple.internal.JInstanceFieldRef) {
            leftOpStr = name.fromSootInstanceFieldRef((JInstanceFieldRef) leftOp);
        } else if (leftOp instanceof soot.jimple.StaticFieldRef) {
            leftOpStr = name.fromSootStaticFieldRef((StaticFieldRef) leftOp);
        } else if (leftOp instanceof soot.jimple.internal.JArrayRef) {
            leftOpStr = name.fromSootJArrayRef((soot.jimple.internal.JArrayRef) leftOp);
        }
        else {
            RJavaCompiler.println("leftOp:" + leftOp.getClass());
            RJavaCompiler.incompleteImplementationError();
        }
        
        return leftOpStr;
    }
    
    private String fromSootRValue(Value rightOp) {
        // right op -> rvalue | imm
        // rvalue -> concreteRef | imm | expr
        // concreteRef -> field | local.field | local[imm]
        // expr -> imm1 binop imm2 | (type) imm | imm instanceof type | invokeExpr | new refType | newarray (type) [imm] | newmultiarray(type)[imm1]...[immn][]* | length imm | neg imm;
        // invokeExpr -> specialinvoke/interfaceinvoke/virtualinvoke local.m(imm1,...,immn) | staticinvoke m(imm1,...,immn)
        String rightOpStr = CLanguageGenerator.INCOMPLETE_IMPLEMENTATION;
        if (rightOp instanceof soot.jimple.StaticFieldRef) {
            rightOpStr = name.fromSootStaticFieldRef((StaticFieldRef) rightOp);
        } 
        else if (rightOp instanceof soot.jimple.internal.JimpleLocal) {
            rightOpStr = name.fromSootLocal((Local) rightOp);
        } 
        else if (rightOp instanceof soot.jimple.internal.JInstanceFieldRef) {
            rightOpStr = name.fromSootInstanceFieldRef((JInstanceFieldRef) rightOp);
        } 
        else if (rightOp instanceof soot.jimple.InvokeExpr) {
            rightOpStr = fromSootInvokeExpr((InvokeExpr) rightOp);
        }
        else if (rightOp instanceof soot.jimple.BinopExpr) {
            rightOpStr = fromSootBinopExpr((BinopExpr) rightOp);
        } 
        else if (rightOp instanceof soot.jimple.NumericConstant) {
            rightOpStr = name.fromSootValue((NumericConstant) rightOp);
        } 
        else if (rightOp instanceof soot.jimple.internal.JNewExpr) {
            rightOpStr = fromSootJNewExpr((JNewExpr) rightOp);
        } 
        else if (rightOp instanceof soot.jimple.StringConstant) {
            rightOpStr = name.fromSootValue((soot.jimple.StringConstant) rightOp);
        } 
        else if (rightOp instanceof soot.jimple.NullConstant) {
            rightOpStr = name.fromSootValue((soot.jimple.NullConstant)rightOp);
        } 
        else if (rightOp instanceof soot.jimple.internal.JLengthExpr) {
            rightOpStr = fromSootJLengthExpr((soot.jimple.internal.JLengthExpr) rightOp);
        } 
        else if (rightOp instanceof soot.jimple.internal.JNewArrayExpr) {
            rightOpStr = fromSootJNewArrayExpr((soot.jimple.internal.JNewArrayExpr) rightOp);
        } 
        else if (rightOp instanceof soot.jimple.internal.JNewMultiArrayExpr) {
            rightOpStr = fromSootJNewMultiArrayExpr((soot.jimple.internal.JNewMultiArrayExpr) rightOp);
        }
        else if (rightOp instanceof soot.jimple.internal.JArrayRef) {
            rightOpStr = name.fromSootJArrayRef((soot.jimple.internal.JArrayRef) rightOp);
        }
        else if (rightOp instanceof soot.jimple.internal.JCastExpr) {
            rightOpStr = fromSootJCastExpr((soot.jimple.internal.JCastExpr) rightOp);
        }
        else if (rightOp instanceof soot.jimple.internal.JInstanceOfExpr) {
            rightOpStr = fromSootJInstanceOfExpr((JInstanceOfExpr) rightOp);
        }
        else if (rightOp instanceof soot.jimple.internal.JNegExpr) {
            rightOpStr = fromSootJNegExpr((JNegExpr)rightOp);
        }
        else {
            RJavaCompiler.println("rightOp:" + rightOp.getClass());
            RJavaCompiler.incompleteImplementationError();
        }
        
        return rightOpStr;
    }

    private String get(RAssignStmt stmt) throws RJavaError {
        JAssignStmt internal = stmt.internal();
        

        Value leftOp = internal.getLeftOp();
        String leftOpStr = fromSootLValue(leftOp);
        
        Value rightOp = internal.getRightOp();
        String rightOpStr = fromSootRValue(rightOp);
        
        // check type
        String rightOpWithCast = typeCasting(leftOp.getType(), rightOp.getType(), rightOpStr);
        
        if (RJavaCompiler.OPT_OBJECT_INLINING) {
            if (leftOp instanceof JInstanceFieldRef) {
                JInstanceFieldRef lInstanceRef = (JInstanceFieldRef) leftOp;
                // if leftOp's base is a by-value local, then we need to find out the actual memory to store
                if (lInstanceRef.getBase() instanceof Local && RLocal.fromSootLocal(generator.getMethodContext(), ((Local) lInstanceRef.getBase())).isByValue()) {
                    RJavaCompiler.println("-checking actual memory store for " + lInstanceRef);
                    List<Value> pointsTo = SemanticMap.pta.tracePointsTo(lInstanceRef.getBase());
                    Value lastNonByValue = null;
                    for (Value v : pointsTo) {
                        RJavaCompiler.print("->" + v);
                        boolean isLocal = v instanceof Local;
                        boolean isLocalInSameContext = false;
                        boolean isLocalByValue = false;
                        if (isLocal) {
                            RLocal rl = RLocal.fromSootLocal(generator.getMethodContext(), (Local) v);
                            isLocalInSameContext = rl != null;
                            if (isLocalInSameContext)
                                isLocalByValue = rl.isByValue();
                        }
                        if ((!isLocal || (isLocal && isLocalInSameContext && !isLocalByValue)) && isValidSootLValue(v)) {
                            lastNonByValue = v;
                            RJavaCompiler.print(" [actualMemoryStore]");
                        }
                        RJavaCompiler.println("");
                    }
                    
                    RJavaCompiler.assertion(lastNonByValue != null, "failed to find an actual memory storage for " + leftOp);
                    
                    leftOpStr = name.fromSootInstanceFieldRef(lInstanceRef, fromSootLValue(lastNonByValue));
                }
            }
            
            if (leftOp instanceof JInstanceFieldRef && RField.fromSootField(((JInstanceFieldRef)leftOp).getField()).isInlinable()) {                
                if (rightOp instanceof JimpleLocal && RLocal.fromSootLocal(generator.getMethodContext(), (Local) rightOp).isByValue())
                    return leftOpStr + " = " + typeCastingByValue(leftOp.getType(), rightOp.getType(), rightOpStr);
                else return leftOpStr + " = *(" + rightOpWithCast + ")";
            }
            
            if (rightOp instanceof JInstanceFieldRef && RField.fromSootField(((JInstanceFieldRef)rightOp).getField()).isInlinable()) {
                if (leftOp instanceof JimpleLocal && RLocal.fromSootLocal(generator.getMethodContext(), (Local) leftOp).isByValue())
                    return leftOpStr + " = " + typeCastingByValue(leftOp.getType(), rightOp.getType(), rightOpStr);
                else return leftOpStr + " = &(" + rightOpWithCast + ")";
            }
        }
        
        return leftOpStr + " = " + rightOpWithCast;        
    }

    private String get(RBreakpointStmt stmt) throws RJavaError {
        throw stmt.newIncompleteImplementationError("BreakpointStmt");
    }
    
    private String get(REnterMonitorStmt stmt) throws RJavaError {
        JEnterMonitorStmt internal = stmt.internal();
        return "pthread_mutex_lock(&(((" + CLanguageRuntime.COMMON_INSTANCE_STRUCT + "*) " + name.fromSootValue(internal.getOp()) + ") -> " + CLanguageRuntime.INSTANCE_MUTEX + "))";
    }
    
    private String get(RExitMonitorStmt stmt) throws RJavaError {
        JExitMonitorStmt internal = stmt.internal();
        return "pthread_mutex_unlock(&(((" + CLanguageRuntime.COMMON_INSTANCE_STRUCT + "*) " + name.fromSootValue(internal.getOp()) + ") -> " + CLanguageRuntime.INSTANCE_MUTEX + "))";
    }
    
    private String get(RGotoStmt stmt) {
        return "goto " + jumpToLabel((AbstractStmt) stmt.internal().getTarget());
    }
    
    private String get(RIdentityStmt stmt) throws RJavaError {
        JIdentityStmt internal = stmt.internal();
        
        String ret = CLanguageGenerator.INCOMPLETE_IMPLEMENTATION;
        Value rightOp = internal.getRightOp();
        if (rightOp instanceof soot.jimple.ParameterRef) {
            // left op
            ret = name.fromSootValue(internal.getLeftOp()) + " = ";
            
            // right op
            soot.jimple.ParameterRef parameterRef = (ParameterRef) rightOp;
            ret += CLanguageGenerator.FORMAL_PARAMETER + parameterRef.getIndex();
        } else if (rightOp instanceof soot.jimple.ThisRef) {
            ret = name.fromSootValue(internal.getLeftOp()) + " = " + CLanguageGenerator.THIS_PARAMETER;
        } else if (rightOp instanceof soot.jimple.internal.JCaughtExceptionRef) {
            ret = exceptionLabel(stmt);
        }
        else {
            throw stmt.newIncompleteImplementationError("rightOp:" + rightOp.getClass());
        }
        
        return ret;
    }
    
    private String get(RIfStmt stmt) {
        JIfStmt internal = stmt.internal();
        String ret = "";
        ret += "if (" + fromSootConditionExpr((ConditionExpr) internal.getCondition()) + ") ";
        ret += "goto " + jumpToLabel((AbstractStmt) internal.getTarget());
        return ret;
    }
    
    private String get(RInvokeStmt stmt) throws RJavaError {
        JInvokeStmt internal = stmt.internal();
        InvokeExpr actualInvoke = internal.getInvokeExpr();
        
        return fromSootInvokeExpr(actualInvoke);
    }
    
    private String get(RNopStmt stmt) {
        // intentionally return empty
        return CLanguageGenerator.SEMICOLON + CLanguageGenerator.comment("nop");
    }
    
    private String get(RRetStmt stmt) throws RJavaError {
        throw stmt.newIncompleteImplementationError("RetStmt");
    }
    
    private String get(RReturnStmt stmt) {
        RType returnType = stmt.getMethod().getReturnType();
        RType valueType = RType.initWithSootType(stmt.internal().getOp().getType());
        return CLanguageGenerator.RETURN + " " + typeCasting(returnType, valueType, name.fromSootValue(stmt.internal().getOp()));
    }
    
    private String get(RReturnVoidStmt stmt) {
        return CLanguageGenerator.RETURN;
    }
    
    private String get(RTableSwitchStmt stmt) {
        JTableSwitchStmt internal = stmt.internal();
        StringBuilder ret = new StringBuilder();
        
        ret.append("switch (" + internal.getKey().toString() + ") {\n");
        for (int i = internal.getLowIndex(); i <= internal.getHighIndex(); i++) {
            ret.append("  case " + i + ":");
            ret.append("goto " + this.jumpToLabel((AbstractStmt) internal.getTarget(i - internal.getLowIndex())) + ";\n");
        }
        ret.append("  default: goto " + this.jumpToLabel((AbstractStmt) internal.getDefaultTarget()) + ";\n");
        ret.append("  }");
        return ret.toString();
    }
    
    private String get(RLookupSwitchStmt stmt) throws RJavaError {
        JLookupSwitchStmt internal = stmt.internal();
        StringBuilder ret = new StringBuilder();
        
        ret.append("switch (" + name.fromSootValue(internal.getKey()) + ") {\n");
        for (int i = 0; i < internal.getLookupValues().size(); i++) {
            ret.append("  case " + internal.getLookupValue(i) + ":");
            ret.append("goto " + jumpToLabel((AbstractStmt) internal.getTarget(i)) + ";\n");
        }
        ret.append("  default: goto " + jumpToLabel((AbstractStmt) internal.getDefaultTarget()) + ";\n");
        ret.append("  }");
        return ret.toString();
    }
    
    private String get(RThrowStmt stmt) throws RJavaError {
        return CLanguageGenerator.comment(stmt.internal().toString());
    }
    
    /*
     * from soot statement/expr representation
     */  
    private String fromSootInvokeExpr(soot.jimple.InvokeExpr invoke) {
        String ret = null;
        if (invoke instanceof soot.jimple.internal.JVirtualInvokeExpr) {
            ret = fromSootJVirtualInvokeExpr((JVirtualInvokeExpr) invoke);
        } else if (invoke instanceof soot.jimple.internal.JSpecialInvokeExpr) {
            ret = fromSootJSpecialInvokeExpr((JSpecialInvokeExpr) invoke);
        } else if (invoke instanceof soot.jimple.internal.JInterfaceInvokeExpr) {
            ret = fromSootJInterfaceInvokeExpr((JInterfaceInvokeExpr) invoke);
        } else if (invoke instanceof soot.jimple.internal.JStaticInvokeExpr) {
            ret = fromSootJStaticInvokeExpr((JStaticInvokeExpr) invoke);
        } else {
            ret = CLanguageGenerator.INCOMPLETE_IMPLEMENTATION;
            RJavaCompiler.incompleteImplementationError();
        }
        if (RClass.fromSootClass(invoke.getMethod().getDeclaringClass()).isAppClass())
            generator.referencing(RMethod.getFromSootMethod(invoke.getMethod()));
        return ret;
    }
    
    
    // FIXME: library call and app call should be unified. 
    private String fromSootJVirtualInvokeExpr(soot.jimple.internal.JVirtualInvokeExpr virtualInvoke) {
        String callingClass = virtualInvoke.getMethod().getDeclaringClass().getName();
        if (callingClass.startsWith("java.") || callingClass.startsWith("javax.") ||
                callingClass.startsWith("org.vmmagic.")) {
            return fromSootJVirtualInvokeExpr_libCall(virtualInvoke);
        } else return fromSootJVirtualInvokeExpr_appCall(virtualInvoke);
    }
    
    /**
     * virtual call into library, we dont do any dispatching related work here.
     * @param virtualInvoke
     * @return
     */
    private String fromSootJVirtualInvokeExpr_libCall(soot.jimple.internal.JVirtualInvokeExpr virtualInvoke) {
        String methodName = name.fromSootMethod(virtualInvoke.getMethod());
        String base = name.fromSootLocal((Local) virtualInvoke.getBase());
         
        String ret = methodName + "(" + base;
        
        if (virtualInvoke.getArgCount() == 0)
            ret += ")";
        else {
            for (int i = 0; i < virtualInvoke.getArgCount(); i++) {
                //ret += ", " + name.fromSootValue(virtualInvoke.getArg(i));
                ret += ", " + typeCastingForInvokeParameter(virtualInvoke, i);
            }
            ret += ")";
        }

        return ret;
    }
    
    /**
     * virtual call into application, we have to solve dispatching here. 
     * @param virtualInvoke
     * @return
     */
    private String fromSootJVirtualInvokeExpr_appCall(soot.jimple.internal.JVirtualInvokeExpr virtualInvoke) {
        /* trying to devirtualize the invoke first */
        Statistics.increaseCounterByOne(Statistics.VIRTUAL_CALL_COUNT);
        String typeInfo = "";
        if (RJavaCompiler.OPT_DEVIRTUALIZATION) {
            Value baseValue = virtualInvoke.getBase();
            Type baseType = baseValue.getType();
            Type inferred;
            
            String typeInferenceInfo = "";
            if (RClass.fromClassName(RType.initWithSootType(baseType).getClassName()).isDefactoFinal()) {
                inferred = baseType;
                
                typeInferenceInfo = baseValue.toString() + "(final) -> " + inferred.toString();
            } else {            
                inferred = SemanticMap.pta.inferType(baseValue);
                
                for (Value v : SemanticMap.pta.tracePointsTo(baseValue)) {
                    typeInferenceInfo += v + "->";
                }
                typeInferenceInfo += inferred != null ? inferred.toString() : "???";
            }
            
            typeInfo = CLanguageGenerator.commentln(typeInferenceInfo);
            
            if (inferred != null) {
                Statistics.increaseCounterByOne("type inference success");
                
                // devirtualize
                Statistics.increaseCounterByOne("devirtualize");
                StringBuilder ret = new StringBuilder();
                
                RType inferredRType = RType.initWithSootType(inferred);
                RClass targetClass = SemanticMap.getRClassFromRType(inferredRType);
                RClass actualClass = RClass.whoImplementsMethodLastInTypeHierarchy(targetClass, RMethod.getFromSootMethod(virtualInvoke.getMethod()));
                RMethod directInvoke = actualClass.getMethodByMatchingNameAndParameters(virtualInvoke.getMethod());
                
                generator.referencing(directInvoke);
                
                ret.append(name.get(actualClass));
                ret.append("_");
                ret.append(name.getFunctionPointerNameFromSootMethod(virtualInvoke.getMethod()));
                ret.append("(");
                
                String base = name.fromSootLocal((Local) virtualInvoke.getBase());
                ret.append(base);
                
                if (virtualInvoke.getArgCount() == 0)
                    ret.append(")");
                else {
                    for (int i = 0; i < virtualInvoke.getArgCount(); i++) {
                        //ret += ", " + name.fromSootValue(virtualInvoke.getArg(i));
                        ret.append(", " + typeCastingForInvokeParameter(virtualInvoke, i));
                    }
                    ret.append(")");
                }
                return ret.toString() + typeInfo;
            }
            
            Statistics.increaseCounterByOne("type inference fail");
        }
        
        /* virtual call */
        
        // for a call to cat.speak()
        // we will have ((Animal_class) ((RJava_Common_Instance*) cat) -> class_struct) -> speak(cat);
        //             1. who declares speak() 2. use common instance to get class_struct
        
        // get who declares speak() first
        RClass baseClass = RClass.fromClassName(virtualInvoke.getBase().getType().toString());
        RClass targetClass = RClass.whoOwnsMethodInTypeHierarchy(baseClass, virtualInvoke.getMethod());
        
        // use class_struct to get function ptr
        String methodName = name.getFunctionPointerNameFromSootMethod(virtualInvoke.getMethod());
        String base = name.fromSootLocal((Local) virtualInvoke.getBase());
        
        StringBuilder ret = new StringBuilder();
        ret.append("(");
        ret.append("(" + name.get(targetClass) + CLanguageRuntime.CLASS_STRUCT_SUFFIX + "*)");
        ret.append("(((" + CLanguageRuntime.COMMON_INSTANCE_STRUCT + "*) " + base + ")");
        ret.append(" -> " + CLanguageRuntime.POINTER_TO_CLASS_STRUCT + "))");
        ret.append(" -> " + methodName + "(" + base);   //base is the first parameter
        
        if (virtualInvoke.getArgCount() == 0)
            ret.append(")");
        else {
            for (int i = 0; i < virtualInvoke.getArgCount(); i++) {
                //ret.append(", " + name.fromSootValue(virtualInvoke.getArg(i)));
                ret.append(", " + typeCastingForInvokeParameter(virtualInvoke, i));
            }
            ret.append(")");
        }
        
        return ret.toString() + typeInfo;
    }
    
    
    private String fromSootJInterfaceInvokeExpr(
            JInterfaceInvokeExpr invoke) {
        // for a class Cat cat which implements interface DoArithmetic, and correspondingly has method calcAdd()
        // we have a pointer (DoArithmetic* cat) which is actually pointing to Cat
        // we invoke calcAdd by ((DoArithmetic*) rjava_get_interface( ((RJava_Common_Class*)((RJava_Common_Instance*) cat) -> class_struct) -> interfaces, "DoArithmetic") ) -> calcAdd();
        
        StringBuilder ret = new StringBuilder();
        
        // get the interface name first
        RClass interfaceClass = RClass.fromClassName(invoke.getBase().getType().toString());
        String methodName = name.getFunctionPointerNameFromSootMethod(invoke.getMethod());
        String base = name.fromSootLocal((Local) invoke.getBase());
        
        ret.append("(");
        ret.append("(" + name.get(interfaceClass) + "*) ");
        String interfaceList = "((" + CLanguageRuntime.COMMON_CLASS_STRUCT + "*)" + 
                    "((" + CLanguageRuntime.COMMON_INSTANCE_STRUCT + "*) " + base + ")" +
                    " -> " + CLanguageRuntime.POINTER_TO_CLASS_STRUCT + ") -> " + CLanguageRuntime.INTERFACE_LIST;
        String interfaceName = "\"" + name.get(interfaceClass) + "\"";
        ret.append(RuntimeHelpers.invoke(RuntimeHelpers.GET_INTERFACE, new String[]{interfaceList, interfaceName}));
        ret.append(")");
        ret.append(" -> " + methodName + "(" + base);
        
        if (invoke.getArgCount() == 0)
            ret.append(")");
        else {
            for (int i = 0; i < invoke.getArgCount(); i++) {
                //ret.append(", " + name.fromSootValue(invoke.getArg(i)));
                ret.append(", " + typeCastingForInvokeParameter(invoke, i));
            }
            ret.append(")");
        }
        
        return ret.toString();
    }
    
    private String fromSootJSpecialInvokeExpr(soot.jimple.internal.JSpecialInvokeExpr specialInvoke) {
        String methodName = name.fromSootMethod(specialInvoke.getMethod());
        String base = name.fromSootLocal((Local) specialInvoke.getBase());
        
        String ret = methodName + "(" + base;
        
        if (specialInvoke.getArgCount() == 0)
            ret += ")";
        else {
            for (int i = 0; i < specialInvoke.getArgCount(); i++) {
                //ret += ", " + name.fromSootValue(specialInvoke.getArg(i));
                ret += ", " + typeCastingForInvokeParameter(specialInvoke, i);
            }
            ret += ")";
        }
        
        return ret;
    }    

    private String fromSootJStaticInvokeExpr(JStaticInvokeExpr actualInvoke) {
        String ret = "";
        ret = name.fromSootMethod(actualInvoke.getMethod());
        
        ret += "(";
        for (int i = 0; i < actualInvoke.getArgCount(); i++) { 
            //ret += name.fromSootValue(actualInvoke.getArg(i));
            ret += typeCastingForInvokeParameter(actualInvoke, i);
            if (i != actualInvoke.getArgCount() - 1)
                ret += ", ";
        }
        ret += ")";
        return ret;
    }
    
    private String fromSootJNegExpr(JNegExpr rightOp) {
        return "-(" + name.fromSootValue(rightOp.getOp()) + ")";
    }
    
    private String fromSootConditionExpr(soot.jimple.ConditionExpr conditionExpr) {
        // if we are comparing two pointers, we need to do a type cast (to avoid gcc warnings)
        if (conditionExpr instanceof JEqExpr || conditionExpr instanceof JNeExpr) {
            RType left = RType.initWithClassName(conditionExpr.getOp1().getType().toString());
            RType right = RType.initWithClassName(conditionExpr.getOp2().getType().toString()); 
            if (left.isReferenceType() && right.isReferenceType()) {
                return "(intptr_t)" + name.fromSootValue(conditionExpr.getOp1()) + " " + conditionExpr.getSymbol() + " (intptr_t)" + name.fromSootValue(conditionExpr.getOp2());
            }
        }
        
        // default
        return name.fromSootValue(conditionExpr.getOp1()) + conditionExpr.getSymbol() + name.fromSootValue(conditionExpr.getOp2());
    }
    
    private String fromSootBinopExpr(soot.jimple.BinopExpr binopExpr) {
        if (binopExpr instanceof JCmpExpr || binopExpr instanceof JCmpgExpr) {
            // Java:
            // a < b in java
            
            // Jimple:
            // int result = a cmpg b
            // if (result < 0) goto true_stmt
            // goto false_stmt
            
            // Java:
            // a == b in java
            
            // Jimple:
            // int result = a cmpg b
            // if (result == 0) goto true_stmt
            // goto false_stmt
            
            // So translated C:
            // int result = (a < b) ? -1 : ((a == b) ? 0 : 1)
            
            String op1 = name.fromSootValue(binopExpr.getOp1());
            String op2 = name.fromSootValue(binopExpr.getOp2());
            
            String ret = "(" + op1 + "<" + op2 + ") ? -1 : ((" + op1 + "==" + op2 + ") ? 0 : 1)";  
            return ret;
        }
        if (binopExpr instanceof JCmplExpr) {
            // Java:
            // a > b
            
            // Jimple:
            // int result = a cmpl b
            // if (result > 0) goto true_stmt
            // goto false_stmt
            
            // So translated C:
            // int result = (a > b) ? : 1 ((a == b) ? 0 : -1)
            String op1 = name.fromSootValue(binopExpr.getOp1());
            String op2 = name.fromSootValue(binopExpr.getOp2());
            String ret = "(" + op1 + ">" + op2 + ") ? 1 : ((" + op1 + "==" + op2 + ") ? 0 : -1)";
            return ret;
        }
        if (binopExpr instanceof JUshrExpr) {
            return "(unsigned int)(" + name.fromSootValue(binopExpr.getOp1()) + " >> " + name.fromSootValue(binopExpr.getOp2()) + ")";
        }
        
        // default
        return name.fromSootValue(binopExpr.getOp1()) + binopExpr.getSymbol() + name.fromSootValue(binopExpr.getOp2());
    }
    
    private String fromSootJNewExpr(soot.jimple.internal.JNewExpr newExpr) {
        // intrinsic happens, and this type becomes a primitive type. just init it to zero
        if (RType.initWithClassName(newExpr.getType().toString()).isPrimitive())
            return "0";
        
        // otherwise, we malloc
        String type = name.fromSootType(newExpr.getType());
        String ret = "(" + type + CLanguageGenerator.POINTER + ") " + CLanguageGenerator.MALLOC + "(";
        ret += CLanguageGenerator.SIZE_OF + "(" + type + "))";
        return ret;
    }
    

    private String fromSootJCastExpr(JCastExpr castExpr) {
        return "(" + name.getWithPointerIfProper(RType.initWithClassName(castExpr.getCastType().toString())) + ")" + castExpr.getOp().toString();
    }
    
    /*
     * RJava array implements
     */
    private String fromSootJLengthExpr(JLengthExpr rightOp) {
        return RuntimeHelpers.invoke(RuntimeHelpers.LENGTH_OF_ARRAY, new String[]{rightOp.getOp().toString() });
    }    

    private String fromSootJNewArrayExpr(JNewArrayExpr rightOp) {
        String length = rightOp.getSize().toString();
        String eleSize = "(long) sizeof(" + name.getWithPointerIfProper(RType.initWithClassName(rightOp.getBaseType().toString())) + ")";
        return RuntimeHelpers.invoke(RuntimeHelpers.NEW_ARRAY, new String[]{length, eleSize});
    }
    
    private String fromSootJNewMultiArrayExpr(JNewMultiArrayExpr rightOp) {
        String dimensionArray = "(int[]){";
        for (int i = 0; i < rightOp.getSizeCount(); i ++) {
            dimensionArray += rightOp.getSize(i);
            if (i != rightOp.getSizeCount() - 1)
                dimensionArray += ",";
        }
        dimensionArray += "}";
        
        String dimensionSize = String.valueOf(rightOp.getSizeCount());
        String eleSize = "sizeof(" + name.fromSootType(rightOp.getBaseType()) + ")";
        return RuntimeHelpers.invoke(RuntimeHelpers.NEW_MULTIARRAY, new String[]{dimensionArray, dimensionSize, eleSize});
    }
    
    /*
     * instanceof expression
     */
    private String fromSootJInstanceOfExpr(JInstanceOfExpr expr) {
        // e.g. temp instanceof org.mmtk.plan.ComplexPhase
        // will translate to a call to helper method rjava_instanceof(temp, &org_mmtk_plan_CompelxPhase_class_instance);
        String ret = RuntimeHelpers.invoke(RuntimeHelpers.INSTANCEOF, new String[]{
                "(void*)" + name.fromSootValue(expr.getOp()),
                "(void*)&" + name.fromSootType(expr.getCheckType()) + CLanguageRuntime.CLASS_STRUCT_INSTANCE_SUFFIX
                });
        return ret;
    }
    
    /*
     * jumping labels
     */
    private String jumpToLabel(AbstractStmt target) {
        if (jumpLabels.containsKey(target.hashCode()))
            return "label" + jumpLabels.get(target.hashCode());
        
        String ret = "label" + labelIndex;
        jumpLabels.put(target.hashCode(), labelIndex);
        labelIndex ++;
        return ret;
    }
    
    private String exceptionLabel(RStatement stmt) {
        Integer storedLabel = jumpLabels.get(stmt.internal().hashCode());
        if (storedLabel == null) {
            String ret = "label" + labelIndex + ":" + CLanguageGenerator.comment("exception handler");
            jumpLabels.put(stmt.internal().hashCode(), labelIndex);
            labelIndex ++;
            return ret;
        }
        else return "label" + storedLabel + ":" + CLanguageGenerator.comment("exception handler");
    }
    
    private String destLabel(RStatement stmt) {
        if (stmt.internal().getBoxesPointingToThis().size() == 0)
            return "";
        
        Integer storedLabel = jumpLabels.get(stmt.internal().hashCode());
        if (storedLabel == null) {
            String ret = "label" + labelIndex + ":";
            jumpLabels.put(stmt.internal().hashCode(), labelIndex);
            labelIndex ++;
            return ret;
        }
        else return "label" + storedLabel + ":";
    }
    
    /*
     * type cast
     */
    /**
     * used when expect and current types are both value (instead of pointer)
     * @param expect
     * @param current
     * @param value
     * @return
     */
    private String typeCastingByValue(Type expect, Type current, String value) {
        if (expect.equals(current))
            return value;
        
        String ret = "(" + name.fromSootType(expect) + ")" + value;
        return ret;
    }
    /**
     * generate typecasting expr
     * @param expect expect type
     * @param current current type
     * @param value current value
     * @return
     */
    private String typeCasting(Type expect, Type current, String value) {
        if (expect.equals(current))
            return value;
        
        return typeCasting(RType.initWithSootType(expect), RType.initWithSootType(current), value);
    }
    
    private String typeCasting(RType expectRType, RType currentRType, String value) {
        if (expectRType.equals(currentRType))
            return value;
        
        String expr = value;
        
        // check if we should unbox the type
        if (expectRType.isPrimitive() && currentRType.isBoxedPrimitiveType() && RType.boxedTypeMatchesPrimitiveType(currentRType, expectRType)) {
            expr = "(" + value + ") -> internal";
        }
        // check if we should box the type
        else if (expectRType.isBoxedPrimitiveType() && currentRType.isPrimitive() && RType.boxedTypeMatchesPrimitiveType(expectRType, currentRType)) {
            String shortBoxedTypeName = expectRType.getClassName().substring(expectRType.getClassName().lastIndexOf('.'));
            expr = "new" + shortBoxedTypeName + "Constant(" + value + ")";
        } 
        // type cast
        // TODO: check type cast
        else //if (expectRType.isReferenceType()) 
            expr = "(" + name.getWithPointerIfProper(expectRType) + ")" + value;
        
        return expr;
    }
    
    private String typeCastingForInvokeParameter(InvokeExpr invoke, int arg) {
        Type expect = invoke.getMethod().getParameterType(arg);
        Type current = invoke.getArg(arg).getType();
        String value = name.fromSootValue(invoke.getArg(arg));
        return typeCasting(expect, current, value);
    }
}
