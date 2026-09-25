package com.parallax.engine.scoring;

import com.parallax.engine.model.EngineInput;
import com.parallax.engine.model.PullType;

/**
 * Small fluent builder for engine inputs in tests. Defaults for the fields SPEC §4 leaves
 * unlisted: independentIncome true, bureauConsent true, addressMismatch false, deceased false,
 * velocity24h 1, ssnIssuanceYear = birthYear + 1, pullType HARD, birthYear = 2026 − age.
 */
final class TestInputs {

    private TestInputs() {
    }

    static In builder() {
        return new In();
    }

    static final class In {
        private int age = 30;
        private int income = 60000;
        private int housing = 1000;
        private int debt = 300;
        private double util = 0.20;
        private int inq = 0;
        private int delq = 0;
        private int trades = 5;
        private int file = 60;
        private boolean indep = true;
        private boolean consent = true;
        private boolean addr = false;
        private boolean dead = false;
        private int velocity = 1;
        private Integer ssnYear = null;

        In age(int v) { age = v; return this; }
        In income(int v) { income = v; return this; }
        In housing(int v) { housing = v; return this; }
        In debt(int v) { debt = v; return this; }
        In util(double v) { util = v; return this; }
        In inq(int v) { inq = v; return this; }
        In delq(int v) { delq = v; return this; }
        In trades(int v) { trades = v; return this; }
        In file(int v) { file = v; return this; }
        In indep(boolean v) { indep = v; return this; }
        In consent(boolean v) { consent = v; return this; }
        In addr(boolean v) { addr = v; return this; }
        In dead(boolean v) { dead = v; return this; }
        In velocity(int v) { velocity = v; return this; }
        In ssnYear(int v) { ssnYear = v; return this; }

        EngineInput build() {
            int birthYear = 2026 - age;
            int ssn = ssnYear == null ? birthYear + 1 : ssnYear;
            return new EngineInput(age, birthYear, income, housing, debt, util, inq, delq, trades, file,
                    indep, consent, addr, ssn, dead, velocity, PullType.HARD);
        }
    }
}
