package com.spacewars.model;

/**
 * RED enemies die on the first hit. BLUE enemies survive a first hit by freezing - their
 * worker thread parks in a sleep loop instead of returning - and only die on a second hit
 * while frozen. Left alone, a frozen BLUE enemy self-thaws after a few seconds and resumes
 * falling, at which point it can be frozen again by the next hit. Whether it's re-hit,
 * thaws, or does both in a loop, its worker is held doing nothing productive the whole time -
 * a deliberate, visible demo of a blocked task tying up thread pool capacity.
 */
public enum EnemyKind {
    RED,
    BLUE
}
