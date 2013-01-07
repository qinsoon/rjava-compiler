package org.rjava.compiler.semantics.representation.stmt;

import org.rjava.compiler.semantics.representation.RStatement;

import soot.Unit;
import soot.jimple.internal.JBreakpointStmt;

public class RBreakpointStmt extends RStatement {

    public RBreakpointStmt(Unit internal) {
	super(internal);
    }
    
    public JBreakpointStmt internal() {
	return (JBreakpointStmt) internal;
    }
}
