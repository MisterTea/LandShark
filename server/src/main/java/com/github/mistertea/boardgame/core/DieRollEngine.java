package com.github.mistertea.boardgame.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;

import junit.framework.Assert;

public abstract class DieRollEngine {
  protected ConcurrentLinkedQueue<String> inputQueue = new ConcurrentLinkedQueue<String>();
  protected Map<String, ConcurrentLinkedQueue<String>> outputQueues = new HashMap<>();
  protected List<String> history = new ArrayList<String>();
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

	protected void snapshot(String serializedState) {
    broadcastMessage(new ServerMessage(ServerMessageType.NEW_STATE,
        System.currentTimeMillis(), serializedState));
    // Add the state to the history
    history.add(serializedState);
  }

  protected void broadcastMessage(ServerMessage serverMessage) {
    String serverMessageString = ThriftB64Utils.ThriftToString(serverMessage);
    for (ConcurrentLinkedQueue<String> outputQueue : outputQueues.values()) {
      outputQueue.add(serverMessageString);
    }
  }

  public void update() {
    if (isGameOver()) {
      return;
    }
    // Process any inputs
    while (!inputQueue.isEmpty()) {
      processInput(inputQueue.poll());
    }
  }

  protected abstract boolean isGameOver();

  protected abstract void processInput(String serializedCommand);
}
