package com.spacewars.model;

/**
 * RED enemies die on the first hit. BLUE enemies survive a first hit by freezing - their
 * worker thread parks in a sleep loop instead of returning - and only die on a second hit
 * while frozen. A BLUE enemy that's never hit again stays frozen forever, permanently holding
 * its pool worker: a deliberate, visible demo of a stuck/blocked task starving a thread pool.
 */
public enum EnemyKind {
    RED,
    BLUE
}
