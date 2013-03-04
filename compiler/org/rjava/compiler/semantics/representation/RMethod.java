package org.rjava.compiler.semantics.representation;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.rjava.compiler.RJavaCompiler;
import org.rjava.compiler.semantics.SootEngine;

import soot.Body;
import soot.Local;
import soot.SootClass;
import soot.SootMethod;
import soot.Type;
import soot.Unit;
import soot.UnitBox;

public class RMethod {
    private List<RType> parameters = new ArrayList<RType>();
    private String name;
    private RType returnType;
    private RClass klass;
    private List<RStatement> body = new ArrayList<RStatement>();
    private List<RLocal> locals = new ArrayList<RLocal>();
    
    private boolean mainMethod;
    
    SootMethod internal;
    
    private boolean intrinsic;
    private String code;
    
    public RMethod(RClass rClass, SootMethod m) {
    	this.internal = m;
    	this.name = m.getName();
    	this.klass = rClass;
    	update();
    }
    
    public void update() {
        parameters.clear();
        body.clear();
        locals.clear();
        
        this.returnType = RType.initWithClassName(internal.getReturnType().toString());
        // get parameter
        for (Object o : internal.getParameterTypes()) {
            Type t = (Type)o;
            parameters.add(RType.initWithClassName(t.toString()));
        }
        
        if (internal.isConcrete()) {
            
            try {
                // get body
                Body sootBody = null;
                if (SootEngine.RUN_SOOT) {
                    sootBody = SootEngine.methodStorage.get(internal);
                }
                else sootBody = internal.retrieveActiveBody();
                        
                Iterator<Unit> iter = sootBody.getUnits().iterator();
                while(iter.hasNext()) {
                    body.add(RStatement.from(this, iter.next()));
                }
                
                Iterator<Local> iter2 = sootBody.getLocals().iterator();
                while(iter2.hasNext()) {
                    locals.add(new RLocal(this, iter2.next()));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        
        checkMainMethod();
    }

    public List<RType> getParameters() {
        return parameters;
    }

    public String getName() {
        return name;
    }

    public RType getReturnType() {
        return returnType;
    }
    
    public String toString() {
	String ret = "<" + returnType.toString() + " " + name + "(";
	for (int i = 0; i < parameters.size(); i++) {
	    ret += parameters.get(i).toString();
	    if (i != parameters.size() - 1)
		ret += ",";
	}
	ret += ")>";
	return ret;
    }

    public List<RStatement> getBody() {
        return body;
    }

    private void checkMainMethod() {
        this.mainMethod =                 // return void
                returnType.isVoidType() &&
                // 1 parameter, array, String
                parameters.size() == 1 && parameters.get(0).isArray() && parameters.get(0).getClassName().equals("java.lang.String") && 
                // method name is main
                name.equals("main");
    }
    
    public boolean isStatic() {
        return internal.isStatic();
    }

    public RClass getKlass() {
        return klass;
    }
    
    public SootMethod internal() {
        return internal;
    }

    public List<RLocal> getLocals() {
        return locals;
    }

    public boolean isIntrinsic() {
        return intrinsic;
    }

    public void setIntrinsic(boolean intrinsic) {
        this.intrinsic = intrinsic;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public boolean isMainMethod() {
        return mainMethod;
    }
    
    public boolean shouldBeInlined() {
        return body.size() <= 25 && RJavaCompiler.getCurrentGeneratorOptions().allowInline();
    }

    /**
     * is this method overriding some method from its super class?
     * @return
     */
    public boolean isOverridingMethod() {
        SootClass superClass = internal.getDeclaringClass().getSuperclass();
        if (superClass != null && superClass.declaresMethod(internal.getName(), internal.getParameterTypes()))
            return true;
        else return false;
    }
    
    public boolean isConstructor() {
        return getName().equals("<init>");
    }
    
    public boolean isClassInitializer() {
        return getName().equals("<clinit>");
    }
}
