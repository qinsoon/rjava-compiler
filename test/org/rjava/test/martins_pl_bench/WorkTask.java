package org.rjava.test.martins_pl_bench;

import org.rjava.restriction.rulesets.RJavaCore;

@RJavaCore
class WorkTask extends Task {

    WorkTask(int id, int pri, Packet wkq, int state, Object v1, Object v2) {
        super(id, pri, wkq, state, v1, v2);
    }

    Task fn(Packet pkt) {
        if (pkt == null) {
            return Bench.taskwait();
        } else {
            int x = Bench.I_HANDLERA + Bench.I_HANDLERB
                    - ((Integer) Bench.v1).intValue();
            Bench.v1 = new Integer(x);

            pkt.id = x;
            // System.out.println("work: pkt.id="+x);
            pkt.a1 = 0;
            for (int i = 0; i <= Bench.BUFSIZE; i++) {
                x = ((Integer) Bench.v2).intValue() + 1;

                if (x > 26)
                    x = 1;
                Bench.v2 = new Integer(x);
                (pkt.a2)[i] = Bench.alphabet.charAt(x);
                // System.out.println("work fn: v2 = "+x+
                // " ch="+(char)(pkt.a2)[i]);
            }
            return Bench.qpkt(pkt);
        }
    }
};
