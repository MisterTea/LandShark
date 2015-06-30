package com.github.mistertea.boardgame.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import junit.framework.Assert;

public abstract class DieRollEngine {
	protected Random rng;

	public DieRollEngine(Random rng) {
		this.rng = rng;
	}

	public List<Integer> rollAndGetDice(DieRoll dieRoll) {
		Assert.assertEquals(0, dieRoll.modifier);

		List<Integer> retval = new ArrayList<Integer>();
		for (int a = 0; a < dieRoll.numDice; a++) {
			retval.add(1 + rng.nextInt(dieRoll.dieSize));
		}
		return retval;
	}

  protected abstract boolean isGameOver();
}
