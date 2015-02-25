package com.github.mistertea.boardgame.landshark;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Map.Entry;
import java.util.Random;

import org.junit.Assert;

public class StateQuery {
  GameBox board;

  public StateQuery(GameBox board) {
    this.board = board;
  }

  public boolean canPutHouses(State currentState, String player, String group,
      int houses) {
    Integer pastHouses = currentState.groupHouseCount.get(group);
    if (pastHouses == null) {
      if (houses > 5) {
        return false;
      }
      if (!playerOwnsGroup(currentState, player, group)) {
        return false;
      }
      return canAffordHouses(currentState, player, group, houses);
    } else {
      // We already have houses, just make sure this doesn't go beyond
      // hotels.
      return (pastHouses + houses) <= 5
          && canAffordHouses(currentState, player, group, houses);
    }
  }

  public boolean canAffordHouses(State currentState, String player,
      String group, int houses) {
    return getPlayerState(currentState, player).cash >= getHousePrice(
        currentState, group) * houses;
  }

  public int getHousePrice(State currentState, String group) {
    return board.propertyGroups.get(group).housePrice;
  }

  public int getRent(State currentState, Property property) {
    String owner = getOwner(currentState, property);
    Assert.assertNotNull(owner);

    if (currentState.groupHouseCount.containsKey(property.group)) {
      return getRentForStreet(board.propertyGroups.get(property.group), true,
          currentState.groupHouseCount.get(property.group));
    } else {
      return getRentForStreet(board.propertyGroups.get(property.group),
          playerOwnsGroup(currentState, owner, property.group), 0);
    }
  }

  public int getRentForStreet(PropertyGroup group, boolean ownsGroup, int houses) {
    if (houses > 0) {
      return group.rent.get(houses);
    } else {
      if (ownsGroup) {
        return group.rent.get(0) * 2;
      }
      return group.rent.get(0);
    }
  }

  public boolean playerOwnsGroup(State currentState, String player, String group) {
    PropertyGroup street = board.propertyGroups.get(group);
    Assert.assertNotNull("Street for group " + group + " is null", street);
    if (getPlayerState(currentState, player).properties
        .containsAll(street.memberNames)) {
      return true;
    }
    return false;
  }

  public String getOwner(State currentState, Property property) {
    for (PlayerState playerState : currentState.playerStates) {
      if (playerState.properties.contains(property.name)) {
        return playerState.name;
      }
    }
    return null;
  }

  public boolean canBuildHouses(State currentState) {
    PlayerState currentPlayerState = getCurrentPlayerState(currentState);
    if (!isActivePlayer(currentPlayerState)) {
      return false;
    }

    for (Entry<String, PropertyGroup> entry : board.propertyGroups.entrySet()) {
      if (currentState.groupHouseCount.get(entry.getKey()) >= 5) {
        // Can't build if there are already hotels
        continue;
      }

      if (currentPlayerState.properties
          .containsAll(entry.getValue().memberNames)) {
        return true;
      }
    }
    return false;
  }

  public int getActivePlayers(State currentState) {
    int count = 0;
    for (PlayerState playerState : currentState.playerStates) {
      if (isActivePlayer(playerState)) {
        count++;
      }
    }
    return count;
  }

  public boolean isActivePlayer(PlayerState playerState) {
    return playerState.cash >= 0 && !playerState.quit;
  }

  public PlayerState getCurrentPlayerState(State currentState) {
    return currentState.playerStates.get(currentState.playerTurn);
  }

  public String getCurrentPlayer(State currentState) {
    return getCurrentPlayerState(currentState).name;
  }

  public PlayerState getPlayerState(State currentState, String name) {
    for (PlayerState playerState : currentState.playerStates) {
      if (playerState.name.equals(name)) {
        return playerState;
      }
    }
    throw new RuntimeException("OOPS");
  }

  public int countUnownedProperties(State inputState) {
    int count = 0;
    for (Property property : board.properties.values()) {
      if (getOwner(inputState, property) == null) {
        count++;
      }
    }
    return count;
  }

  public GameBox getBoard() {
    return board;
  }

  public int countOwnedInGroup(State currentState, String group, String owner) {
    PlayerState ownerState = getPlayerState(currentState, owner);
    int count = 0;
    for (String property : board.propertyGroups.get(group).memberNames) {
      if (ownerState.properties.contains(property)) {
        count++;
      }
    }
    return count;
  }

  public int estimateFutureRent(State currentState, Property newProperty,
      String newOwner) {
    State tmpState = currentState.deepCopy();
    getPlayerState(tmpState, newOwner).properties.add(newProperty.name);

    // TODO: This is the mean of the binomial distribution where the number
    // of chances is
    // equal to the probability of landing (i.e. the number of
    // turns is equal to the number of spaces on the board, and every space
    // has an equal chance of being landed on.). Note that you "gain" rent
    // by landing on your own square, to account for the opportunity cost.
    return getRent(tmpState, newProperty) * getActivePlayers(currentState);
  }
}
