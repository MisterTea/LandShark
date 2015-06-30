package com.github.mistertea.boardgame.landshark.player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;
import java.util.Random;

import com.github.mistertea.boardgame.landshark.LandsharkBox;
import com.github.mistertea.boardgame.landshark.LandsharkCommand;
import com.github.mistertea.boardgame.landshark.LandsharkCommandType;
import com.github.mistertea.boardgame.landshark.LandsharkState;
import com.github.mistertea.boardgame.landshark.PlayerState;
import com.github.mistertea.boardgame.landshark.PropertyGroup;
import com.github.mistertea.boardgame.landshark.StateQuery;

public class SimplePlayer extends AbstractPlayer {
	protected Random rng;

	public SimplePlayer(String name) {
		super(name);
		rng = new Random(name.hashCode());
	}

	@Override
	public LandsharkCommand fetchCommand(StateQuery query, LandsharkState state) {
	  LandsharkBox board = query.getBoard();
		PlayerState myState = query.getPlayerState(state, name);

		switch (state.turnState) {
		case END_OF_TURN:
		  List<LandsharkCommand> possibleEndCommands = new ArrayList<>();
	    for (Entry<String, PropertyGroup> entry : board.propertyGroups.entrySet()) {
	      String group = entry.getKey();
	      if (state.groupHouseCount.get(group) >= 5) {
	        // Can't build if there are already hotels
	        continue;
	      }

	      if (myState.properties
	          .containsAll(entry.getValue().memberNames)) {
	        if (query.canAffordHouses(state, name, group)) {
	          possibleEndCommands.add(new LandsharkCommand().setPlayer(name).setType(LandsharkCommandType.BUY_HOUSES).setHousePurchaseGroup(group));
	        }
	      }
	    }
			possibleEndCommands.add(new LandsharkCommand()
					.setPlayer(name)
					.setType(LandsharkCommandType.PASS_TURN));
			Collections.shuffle(possibleEndCommands, rng);
			return possibleEndCommands.get(0);
		case AUCTION: {
			int bid = query.estimateFutureRent(state,
					board.properties.get(state.auctionState.property), name)
					+ rng.nextInt(50);
			switch(query.countOwnedInGroup(state, query.getGroupForProperty(state.auctionState.property), name)) {
			case 0:
			  break;
			case 1:
			  bid *= 2;
			  break;
			case 2:
			  bid *= 4;
			  break;
      case 3:
        bid *= 8;
        break;
			}
			int maximumBidGivenRisk = Math.max(
					0,
					myState.cash
							- 100); // TODO: Fix risk estimate
			bid = Math.min(bid, maximumBidGivenRisk);
			return new LandsharkCommand()
					.setPlayer(name)
					.setType(LandsharkCommandType.BID_AUCTION).setBid(bid);
		}
		default:
			throw new RuntimeException("OOPS");
		}
	}

}
