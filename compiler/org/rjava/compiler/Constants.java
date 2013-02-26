package org.rjava.compiler;

import org.rjava.compiler.semantics.SemanticMap;
import org.rjava.compiler.semantics.representation.RClass;
import org.rjava.compiler.semantics.representation.RMethod;

public final class Constants {
    // rjava source file extension, same as Java at the moment
    public static final String RJAVA_EXT = ".java";
    
    public static final String OUTPUT_DIR = "output/";
    
    // rjava restr./ext. annotations' path
    public static final String RJAVA_ANNOTATION_DIR = "rjava/";
    public static final String RJAVA_MAGIC_DIR = "rjava/";
    
    public static final String RJAVA_RESTRICTION_RULE_PACKAGE = "org.rjava.restriction.rules";
    public static final String RJAVA_RESTRICTION_RULE_PRAGMA = "org.rjava.restriction.RestrictionRule";
    public static final String RJAVA_RESTRICTION_RULESET_PACKAGE = "org.rjava.restriction.rulesets";
    public static final String RJAVA_RESTRICTION_RULESET_PRAGMA = "org.rjava.restriction.RestrictionRuleset";
    
    // the suffix that check rules end with
    public static final String CHECK_RULE_SUFFIX = "_CHECK";
    public static final String CHECK_CLASS_METHOD = "checkClass";
    public static final Class[] CHECK_CLASS_PARA = new Class[] {
	RClass.class
    };
    public static final String CHECK_METHOD_METHOD = "checkMethod";
    public static final Class[] CHECK_METHOD_PARA = new Class[] {
	RMethod.class
    };

    public static final String MAGIC_ADDRESS            = "org.vmmagic.unboxed.Address";
    public static final String MAGIC_EXTENT             = "org.vmmagic.unboxed.Extent";
    public static final String MAGIC_OBJECTREFERENCE    = "org.vmmagic.unboxed.ObjectReference";
    public static final String MAGIC_OFFSET             = "org.vmmagic.unboxed.Offset";
    public static final String MAGIC_WORD               = "org.vmmagic.unboxed.Word";
    
    public static final String MAGIC_ARRAY_SUFFIX       = "Array";
    
    public static final String[] MAGIC_TYPES = {
        MAGIC_ADDRESS,
        MAGIC_EXTENT,
        MAGIC_OBJECTREFERENCE,
        MAGIC_OFFSET,
        MAGIC_WORD
    };

    public static final String[] MAGIC_ARRAY_TYPES = {
        MAGIC_ADDRESS           + MAGIC_ARRAY_SUFFIX,
        MAGIC_EXTENT            + MAGIC_ARRAY_SUFFIX,
        MAGIC_OBJECTREFERENCE   + MAGIC_ARRAY_SUFFIX,
        MAGIC_OFFSET            + MAGIC_ARRAY_SUFFIX,
        MAGIC_WORD              + MAGIC_ARRAY_SUFFIX
    };

    
    private Constants(){}
}
