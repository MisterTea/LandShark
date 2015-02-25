package com.github.mistertea.boardgame.landshark.player;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import com.github.mistertea.boardgame.core.ThriftB64Utils;
import com.github.mistertea.boardgame.landshark.GameBox;
import com.github.mistertea.boardgame.landshark.LandsharkCommand;
import com.github.mistertea.boardgame.landshark.LandsharkCommandType;
import com.github.mistertea.boardgame.landshark.PlayerState;
import com.github.mistertea.boardgame.landshark.Property;
import com.github.mistertea.boardgame.landshark.State;
import com.github.mistertea.boardgame.landshark.StateQuery;

public class SimplePlayer extends AbstractPlayer {
	protected Random rng;

	public SimplePlayer(String name) {
		super(name);
		rng = new Random(name.hashCode());
	}

	@Override
	public String fetchCommand(StateQuery query, State state) {
	  GameBox board = query.getBoard();
		PlayerState myState = query.getPlayerState(state, name);

		switch (state.turnState) {
		case END_OF_TURN:
			return ThriftB64Utils.ThriftToString(new LandsharkCommand()
					.setPlayer(name)
					.setType(LandsharkCommandType.PASS_TURN.getValue()));
		case AUCTION: {
			int bid = query.estimateFutureRent(state,
					board.properties.get(state.auctionState.property), name)
					+ rng.nextInt(50);
			int maximumBidGivenRisk = Math.max(
					0,
					myState.cash
							- 100); // TODO: Fix risk estimate
			bid = Math.min(bid, maximumBidGivenRisk);
			return ThriftB64Utils.ThriftToString(new LandsharkCommand()
					.setPlayer(name)
					.setType(LandsharkCommandType.BID_AUCTION.getValue()).setBid(bid));
		}
		default:
			throw new RuntimeException("OOPS");
		}
	}

}
