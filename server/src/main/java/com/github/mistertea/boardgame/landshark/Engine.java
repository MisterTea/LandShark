package com.github.mistertea.boardgame.landshark;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.apache.thrift.TException;
import org.apache.thrift.protocol.TJSONProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.transport.TIOStreamTransport;
import org.apache.thrift.transport.TTransport;
import org.junit.Assert;

import com.github.mistertea.boardgame.core.CoreCommand;
import com.github.mistertea.boardgame.core.CoreCommandType;
import com.github.mistertea.boardgame.core.DieRollEngine;
import com.github.mistertea.boardgame.core.ServerMessage;
import com.github.mistertea.boardgame.core.ServerMessageType;
import com.github.mistertea.boardgame.core.ThriftB64Utils;
import com.github.mistertea.boardgame.landshark.player.AbstractPlayer;
import com.github.mistertea.boardgame.landshark.player.ConsolePlayer;
import com.github.mistertea.boardgame.landshark.player.SimplePlayer;

public class Engine extends DieRollEngine {
  private static final int DIVIDEND_PAYOUT = 200;
  private GameBox board;
  private State currentState;
  private StateQuery query;
  private Stats stats;

  public Engine(Random rng, GameBox board, String gameName, List<String> players) {
    super(rng);
    this.board = board;
    this.query = new StateQuery(board);
    this.stats = new Stats();
    initialize(gameName, players);
  }

  private void initialize(String gameName, List<String> players) {
    // Init stats
    for (String propertyName : board.properties.keySet()) {
      stats.propertyOwnerStats
          .put(propertyName, new ArrayList<PropertyStats>());
    }
    currentState = new State().setId(gameName);

    for (String groupName : board.propertyGroups.keySet()) {
      currentState.groupHouseCount.put(groupName, 0);
    }

    for (String actionName : board.actions.keySet()) {
      currentState.actionCardOrder.add(actionName);
    }
    Collections.shuffle(currentState.actionCardOrder, rng);

    for (String playerName : players) {
      PlayerState currentPlayer = new PlayerState().setName(playerName)
          .setCash(board.startingMoney);
      currentState.playerStates.add(currentPlayer);
    }

    beginNewTurn();
  }

  protected void processInput(String input) {
    CoreCommand coreCommand;
    try {
      coreCommand = ThriftB64Utils.stringToThrift(input, CoreCommand.class);
    } catch (IOException e) {
      // Drop the packet if it cannot be parsed
      e.printStackTrace();
      return;
    }

    try {
      if (coreCommand.type < 1000) {
        CoreCommandType type = CoreCommandType.findByValue(coreCommand.type);
        if (type == null) {
          throw new IOException("Unknown command type: " + coreCommand.type);
        }
        switch (type) {
        case CHAT:
          String chatMessage = "<" + coreCommand.player + "> "
              + coreCommand.chat;
          broadcastMessage(new ServerMessage(ServerMessageType.CHAT,
              System.currentTimeMillis(), chatMessage));
          break;
        case QUIT:
          handlePlayerDeath(coreCommand.player, "quit");
          break;
        default:
          break;
        }
      } else {
        LandsharkCommand landsharkCommand;
        try {
          landsharkCommand = ThriftB64Utils.stringToThrift(input,
              LandsharkCommand.class);
        } catch (IOException e) {
          // Drop the packet if it cannot be parsed
          e.printStackTrace();
          return;
        }

        LandsharkCommandType type = LandsharkCommandType
            .findByValue(landsharkCommand.type);
        if (type == null) {
          throw new IOException("Unknown command type: "
              + landsharkCommand.type);
        }
        switch (type) {
        case BID_AUCTION:
          if (currentState.auctionState == null) {
            throw new IOException("Tried to bid when not in an auction: "
                + landsharkCommand);
          }
          currentState.auctionState.bids.put(landsharkCommand.player,
              landsharkCommand.bid);
          if (currentState.auctionState.bids.size() == query
              .getActivePlayers(currentState)) {
            completeAuction();
          }
          break;
        case PASS_AUCTION:
          // Don't let players take back their auction
          if (!currentState.auctionState.bids
              .containsKey(landsharkCommand.player)) {
            currentState.auctionState.bids.put(landsharkCommand.player, 0);
            if (currentState.auctionState.bids.size() == query
                .getActivePlayers(currentState)) {
              completeAuction();
            }
          }
          break;
        case BUY_HOUSES:
          Assert.assertEquals(TurnState.END_OF_TURN, currentState.turnState);
          Assert.assertFalse(landsharkCommand.housePurchases.isEmpty());
          for (Map.Entry<String, Integer> entry : landsharkCommand.housePurchases
              .entrySet()) {
            String group = entry.getKey();
            int houses = entry.getValue();
            if (!query.canPutHouses(currentState, landsharkCommand.player,
                group, houses)) {
              query.canPutHouses(currentState, landsharkCommand.player, group,
                  houses);
              throw new IOException("Invalid house allocation: "
                  + landsharkCommand.player + " " + group + " " + houses);
            } else {
              putHouses(group, houses);
              finishEndOfTurn();
            }
          }
          break;
        case CREATE_AUCTION:
          Assert.assertTrue(currentState.turnState == TurnState.END_OF_TURN);
          Assert.assertNotNull(landsharkCommand.property);
          Assert.assertEquals(
              "Invalid auction choice: " + landsharkCommand.toString(),
              query.getOwner(currentState,
                  board.properties.get(landsharkCommand.property)),
              landsharkCommand.player);
          currentState.turnState = TurnState.AUCTION;
          currentState.auctionState = new AuctionState().setProperty(
              landsharkCommand.property).setAuctionOwner(
              landsharkCommand.player);
          snapshot(landsharkCommand.player + " started an auction for "
              + landsharkCommand.property);
          break;
        case PASS_TURN:
          snapshot(landsharkCommand.player + " passes the end-of-turn action");
          finishEndOfTurn();
          break;
        default:
          break;
        }
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
      // ServerMessage errorMessage = new ServerMessage(
      // ServerMessageType.ERROR, System.currentTimeMillis(),
      // "Error processing input: " + e.getMessage());
      // outputQueues.get(coreCommand.player).add(
      // ThriftB64Utils.ThriftToString(errorMessage));
    }
  }

  private void putHouses(String property, int houses) {
    String owner = query.getOwner(currentState, board.properties.get(property));
    Assert.assertTrue(query.canAffordHouses(currentState, owner, property,
        houses));
    String group = board.properties.get(property).group;
    Integer pastHouses = currentState.groupHouseCount.get(group);
    if (pastHouses == null) {
      pastHouses = 0;
    }
    currentState.groupHouseCount.put(group, pastHouses + houses);
    List<PropertyStats> propertyOwnerStats = stats.propertyOwnerStats
        .get(property);

    // TODO: The fact that you buy houses later is neglected here. Maybe
    // create a new entry?
    int cost = query.getHousePrice(currentState, property) * houses;
    query.getPlayerState(currentState, owner).cash -= cost;
    Assert.assertTrue(query.getPlayerState(currentState, owner).cash >= 0);

    PropertyStats ownerStats = propertyOwnerStats
        .get(propertyOwnerStats.size() - 1);
    ownerStats.investment = ownerStats.investment + cost;

    snapshot(query.getPlayerState(currentState, owner).name + " bought "
        + houses + " on his " + group + " properties.");
  }

  private void completeAuction() {
    AuctionState auctionState = currentState.auctionState;
    Property auctionProperty = board.properties.get(auctionState.property);

    // Get the highest bidder
    String highestBidder = null;
    for (Map.Entry<String, Integer> entry : auctionState.bids.entrySet()) {
      if (highestBidder == null) {
        highestBidder = entry.getKey();
      } else {
        int highestBid = auctionState.bids.get(highestBidder);
        if (highestBid == entry.getValue()) {
          // Break ties based on turn in clockwise order.
          int onPlayer = currentState.playerTurn;
          while (true) {
            if (currentState.playerStates.get(onPlayer).name
                .equals(highestBidder)) {
              // The current highest bidder is still the highest
              break;
            }
            if (currentState.playerStates.get(onPlayer).name.equals(entry
                .getKey())) {
              // The current highest bidder loses based on turn order
              highestBidder = entry.getKey();
              break;
            }
          }
        } else if (highestBid < entry.getValue()) {
          highestBidder = entry.getKey();
        }
      }
    }

    // Someone wins the auction
    PlayerState highestBidderState = query.getPlayerState(currentState,
        highestBidder);
    int winningBid = auctionState.bids.get(highestBidder);
    String message;
    if (winningBid <= 0) {
      // No one bid a positive number, skip the auction
      message = "No one bid on the auction.  Auction cancelled.";
    } else {
      if (auctionState.auctionOwner == null
          || highestBidder.equals(auctionState.auctionOwner)) {
        // The winner has to pay the bank
        highestBidderState.cash -= winningBid;
        message = highestBidder + " won the auction and pays " + winningBid
            + " to the bank";
      } else {
        // The winner pays someone else
        highestBidderState.cash -= winningBid;
        PlayerState seller = query.getPlayerState(currentState,
            auctionState.auctionOwner);
        seller.cash += winningBid;
        seller.properties.remove(auctionState.property);
        PropertyStats propertyStats = getLatestPropertyStats(auctionState.property);
        propertyStats.revenue += winningBid;
        message = highestBidder + " won the auction and pays " + winningBid
            + " to " + seller.name;
      }
      highestBidderState.properties.add(currentState.auctionState.property);
      boolean winningCreatesGroup = query.playerOwnsGroup(currentState,
          highestBidder, auctionProperty.group);
      stats.propertyOwnerStats.get(auctionState.property).add(
          new PropertyStats(highestBidder, winningBid, 0, 0,
              winningCreatesGroup, 1));
    }
    currentState.turnState = TurnState.AUCTION_RESULTS;
    snapshot(message);
    currentState.auctionState = null;
    finishEndOfTurn();
  }

  private PropertyStats getLatestPropertyStats(String property) {
    List<PropertyStats> statsList = stats.propertyOwnerStats.get(property);
    Assert.assertFalse(statsList.isEmpty());
    return statsList.get(statsList.size() - 1);
  }

  private void beginNewTurn() {
    PlayerState currentPlayerState = query.getCurrentPlayerState(currentState);
    currentState.turnState = TurnState.DRAWING_ACTION;
    snapshot(currentPlayerState.name + " draws an action card.");

    // Check if we need to shuffle
    if (currentState.nextActionCard == currentState.actionCardOrder.size()) {
      // Shuffle
      currentState.nextActionCard = 0;
      Collections.shuffle(currentState.actionCardOrder, rng);
    }

    // Draw it!
    String actionName = currentState.actionCardOrder
        .get(currentState.nextActionCard++);
    Action action = board.actions.get(actionName);

    switch (action.type) {
    case DIVIDEND:
      currentPlayerState.cash += DIVIDEND_PAYOUT;
      for (PlayerState playerState : currentState.playerStates) {
        playerState.cash += DIVIDEND_PAYOUT;
      }
      currentState.turnState = TurnState.COLLECTING_DIVIDEND;
      snapshot(currentPlayerState.name + " draws a dividend card and collects "
          + (DIVIDEND_PAYOUT * 2) + ".  Everyone else collects "
          + DIVIDEND_PAYOUT + ".");
      finishAction();
      break;
    case PROPERTY: {
      Property property = board.properties.get(actionName);
      String owner = query.getOwner(currentState, property);
      if (owner != null && owner.equals(query.getCurrentPlayer(currentState))) {
        // Landed on one's own property, do nothing
        snapshot(currentPlayerState.name + " landed on his/her own property");
        finishAction();
      } else if (owner != null) {
        // Pay owner
        int rent = query.getRent(currentState, property);
        currentPlayerState.cash -= rent;
        query.getPlayerState(currentState, owner).cash += rent;
        getLatestPropertyStats(property.name).revenue += rent;
        currentState.turnState = TurnState.PAYING_RENT;
        snapshot(currentPlayerState.name + " pays " + rent + " to " + owner
            + " for rent on " + property.name);
        finishAction();
      } else {
        // Start auction

        currentState.turnState = TurnState.AUCTION;
        currentState.auctionState = new AuctionState()
            .setProperty(property.name);
        snapshot(currentPlayerState.name + " starts an auction for "
            + property.name);
      }
      break;
    }
    case TAXES:
      currentPlayerState.cash -= DIVIDEND_PAYOUT / 2;
      for (PlayerState playerState : currentState.playerStates) {
        playerState.cash -= DIVIDEND_PAYOUT / 2;
        currentState.message = currentPlayerState.name
            + " draws a taxes and loses " + DIVIDEND_PAYOUT
            + ".  Everyone else loses " + (DIVIDEND_PAYOUT / 2) + ".";
      }
      break;
    default:
      break;

    }
  }

  private boolean handlePlayerDeath(String playerName, String reason) {
    PlayerState playerState = query.getPlayerState(currentState, playerName);
    if (playerState.quit || playerState.cash < 0) {
      for (String propertyName : playerState.properties) {
        String group = board.properties.get(propertyName).group;
        currentState.groupHouseCount.put(group, 0);
      }
      playerState.properties.clear();
      snapshot(playerName + " is removed from the game.  Reason: " + reason
          + ".");
      return true;
    }
    return false;
  }

  private void finishAction() {
    // Handle player death (if appropriate)
    if (handlePlayerDeath(query.getCurrentPlayer(currentState), "bankrupt")) {
      finishEndOfTurn();
      return;
    }

    // If we can take an end-of-turn action, set the state so the user can opt
    // to take an action.
    if (query.canBuildHouses(currentState)
        || !query.getCurrentPlayerState(currentState).properties.isEmpty()) {
      currentState.turnState = TurnState.END_OF_TURN;
      snapshot(query.getCurrentPlayer(currentState)
          + " can take an end-of-turn action.");
      return;
    } else {
      finishEndOfTurn();
    }
  }

  private void finishEndOfTurn() {
    while (true) {
      currentState.playerTurn = (currentState.playerTurn + 1)
          % currentState.playerStates.size();
      if (query.isActivePlayer(query.getCurrentPlayerState(currentState))) {
        beginNewTurn();
        return;
      }
    }
  }

  protected void snapshot(String message) {
    currentState.message = message;
    String serializedState = ThriftB64Utils.ThriftToString(currentState);
    super.snapshot(serializedState);
    System.out.println("CURRENT STATE: " + currentState.toString());
    currentState.message = null;
  }

  protected boolean isGameOver() {
    return query.getActivePlayers(currentState) <= 1;
  }

  public static void main(String[] args) throws IOException, TException {
    GameBox newBoard = new GameBox();
    {
      TTransport trans = new TIOStreamTransport(
          new FileInputStream("Board.tft"));
      TProtocol prot = new TJSONProtocol(trans);
      newBoard.read(prot);
      trans.close();
    }

    List<String> playerNames = new ArrayList<String>();
    playerNames.add("a");
    playerNames.add("b");
    playerNames.add("c");
    playerNames.add("d");
    Engine engine = new Engine(new Random(1L), newBoard, "GameName",
        playerNames);

    Map<String, AbstractPlayer> playerControllers = new HashMap<>();
    playerControllers.put("a", new ConsolePlayer("a"));
    // playerControllers.put("a", new SimplePlayer("a"));
    playerControllers.put("b", new SimplePlayer("b"));
    playerControllers.put("c", new SimplePlayer("c"));
    playerControllers.put("d", new SimplePlayer("d"));

    while (!engine.isGameOver()) {
      // Poll for inputs
      for (AbstractPlayer playerController : playerControllers.values()) {
        if (!engine.query.isActivePlayer(engine.query.getPlayerState(
            engine.currentState, playerController.getName()))) {
          // Player is not active
          continue;
        }

        if (engine.currentState.turnState != TurnState.AUCTION
            && !engine.query.getCurrentPlayer(engine.currentState).equals(
                playerController.getName())) {
          // It isn't this player's turn.
          continue;
        }

        String command = playerController.fetchCommand(engine.query,
            engine.currentState);
        if (command == null) {
          continue;
        }
        if (command.isEmpty()) {
          System.out.println("ABORTING GAME");
          // Special command to abort
          break;
        }
        engine.inputQueue.add(command);
      }
      engine.update();
    }
    System.out.println("GAME OVER");
  }
}
