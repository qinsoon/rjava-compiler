/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.mmtk.plan.semispace.gctrace;

import org.mmtk.plan.semispace.SSConstraints;

import org.vmmagic.pragma.*;

/**
 * GCTrace constants.
 */
@Uninterruptible
public class GCTraceConstraints extends SSConstraints {
  @Override
  public boolean needsObjectReferenceWriteBarrier() { return true; }
  @Override
  public boolean generateGCTrace() { return true; }
}
