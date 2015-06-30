package com.github.mistertea.boardgame.landshark.player;

import com.github.mistertea.boardgame.core.B64Utils;
import com.github.mistertea.boardgame.landshark.LandsharkBox;
import com.github.mistertea.boardgame.landshark.LandsharkCommand;
import com.github.mistertea.boardgame.landshark.LandsharkCommandType;
import com.github.mistertea.boardgame.landshark.PlayerState;
import com.github.mistertea.boardgame.landshark.Property;
import com.github.mistertea.boardgame.landshark.LandsharkState;
import com.github.mistertea.boardgame.landshark.StateQuery;

public class GroupGrabberPlayer extends SimplePlayer {
  public GroupGrabberPlayer(String name) {
    super(name);
  }

  @Override
  public LandsharkCommand fetchCommand(StateQuery query, LandsharkState state) {
    LandsharkBox board = query.getBoard();
    PlayerState myState = query.getPlayerState(state, name);

    switch (state.turnState) {
    case AUCTION: {
      Property property = board.properties.get(state.auctionState.property);
      int bid = query.estimateFutureRent(state, property, name)
          + rng.nextInt(50);

      int groupSize = board.propertyGroups.get(property.group).memberNames
          .size();
      int numAlreadyOwned = query
          .countOwnedInGroup(state, property.group, name);
      int leftToOwn = groupSize - numAlreadyOwned;

      switch (leftToOwn) {
      case 0:
        // You are about to lose one of your own monopolies
        bid *= 15;
        break;
      case 1:
        // You can get a monopoly
        bid *= 10;
        break;
      case 2:
        // Getting 2 of 3
        bid *= 5;
      default:
        break;
      }

      int maximumBidGivenRisk = Math.max(0, myState.cash - 100); // TODO: figure out better risk metric
      bid = Math.min(bid, maximumBidGivenRisk);
      return new LandsharkCommand()
          .setPlayer(name).setType(LandsharkCommandType.BID_AUCTION)
          .setBid(bid);
    }
    default:
      return super.fetchCommand(query, state);
    }
  }

}
