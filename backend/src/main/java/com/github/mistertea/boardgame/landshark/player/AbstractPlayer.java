package com.github.mistertea.boardgame.landshark.player;

import com.github.mistertea.boardgame.landshark.LandsharkCommand;
import com.github.mistertea.boardgame.landshark.LandsharkState;
import com.github.mistertea.boardgame.landshark.StateQuery;

public abstract class AbstractPlayer {
	protected String name;

	public AbstractPlayer(String name) {
		this.name = name;
	}

	public String getName() {
		return name;
	}

	public abstract LandsharkCommand fetchCommand(StateQuery query, LandsharkState state);
}
