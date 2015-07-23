package com.github.mistertea.boardgame.landshark;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.apache.thrift.TException;
import org.junit.Assert;
import org.mongojack.DBSort;
import org.mongojack.JacksonDBCollection;

import com.github.mistertea.boardgame.core.B64Utils;
import com.github.mistertea.boardgame.core.DieRollEngine;
import com.github.mistertea.boardgame.landshark.player.AbstractPlayer;
import com.github.mistertea.boardgame.landshark.player.SimplePlayer;
import com.github.mistertea.thriftmongojack.ThriftJacksonDBCollection;
import com.github.mistertea.thriftmongojack.ThriftMongoJackService;
import com.google.common.collect.Lists;
import com.mongodb.DB;
import com.mongodb.MongoClient;

public class LandSharkEngine extends DieRollEngine {
	private static final int DIVIDEND_PAYOUT = 200;
	private LandsharkBox board;
	private LandsharkState currentState;
	private StateQuery query;
	private LandsharkGame game;

	public LandSharkEngine(Random rng, LandsharkBox board, LandsharkGame game) {
		super(rng);
		this.board = board;
		this.game = game;
		this.query = new StateQuery(board);
	}

	public LandSharkEngine(LandsharkBox board, LandsharkState currentState) {
		super(B64Utils.<Random> stringToObject(currentState.serializedRng));
		this.board = board;
		this.query = new StateQuery(board);
		this.currentState = currentState;
	}

	private void initialize() {
		// Init stats
		Stats stats = new Stats();
		for (String propertyName : board.properties.keySet()) {
			stats.propertyOwnerStats.put(propertyName,
					new ArrayList<PropertyStats>());
		}
		currentState = new LandsharkState().setGameId(game._id).setStats(stats);

		for (String groupName : board.propertyGroups.keySet()) {
			currentState.groupHouseCount.put(groupName, 0);
		}

		for (String actionName : board.actions.keySet()) {
			currentState.actionCardOrder.add(actionName);
		}
		Collections.shuffle(currentState.actionCardOrder, rng);

		for (String playerName : game.players) {
			PlayerState currentPlayer = new PlayerState().setName(playerName)
					.setCash(board.startingMoney);
			currentState.playerStates.add(currentPlayer);
		}

		beginNewTurn();
	}

	protected boolean processInput(LandsharkCommand command) throws IOException {
		switch (command.type) {
		case JOIN_GAME:
		case OBSERVE_GAME:
		case LEAVE_GAME:
		case CHAT:
			break;
		case BID_AUCTION: {
			if (!game.started) {
				return false;
			}
			if (currentState.auctionState == null) {
				return false;
			}
			Integer bid = currentState.auctionState.bids.get(command.player);
			if (bid != null && bid >= command.bid) {
				return false;
			}
		}
			break;
		default: {
			if (!game.started) {
				return false;
			}
			if (!query.getCurrentPlayer(currentState).equals(command.player)) {
				return false;
			}
			break;
		}
		}

		if (game.commands.isEmpty()) {
			game.commands.add(command);
		} else {
			int position = Collections.binarySearch(game.commands, command,
					new Comparator<LandsharkCommand>() {
						@Override
						public int compare(LandsharkCommand o1,
								LandsharkCommand o2) {
							return Long.compare(o1.timestamp, o2.timestamp);
						}
					});
			game.commands.add(position, command);
		}

		switch (command.type) {
		case JOIN_GAME:
			break;
		case OBSERVE_GAME:
			game.observers.add(command.player);
			break;
		case LEAVE_GAME:
			if (game.started) {
				PlayerState playerState = query.getPlayerState(currentState,
						command.player);
				handlePlayerDeath(playerState, "quit");
			}
			break;
		case CHAT:
			break;
		case BID_AUCTION:
			currentState.auctionState.bids.put(command.player, command.bid);
			if (currentState.auctionState.bids.size() == query
					.getActivePlayers(currentState)) {
				completeAuction();
			} else {
				System.out.println("STILL WAITING ON MORE BIDS: "
						+ currentState.auctionState);
			}
			break;
		case PASS_AUCTION:
			// Don't let players take back their auction
			if (!currentState.auctionState.bids.containsKey(command.player)) {
				currentState.auctionState.bids.put(command.player, 0);
				if (currentState.auctionState.bids.size() == query
						.getActivePlayers(currentState)) {
					completeAuction();
				}
			}
			break;
		case BUY_HOUSES:
			Assert.assertEquals(TurnState.END_OF_TURN, currentState.turnState);
			Assert.assertNotNull(command.housePurchaseGroup);
			String group = command.housePurchaseGroup;
			if (!query.canPutHouses(currentState, command.player, group)) {
				query.canPutHouses(currentState, command.player, group);
				throw new IOException("Invalid house allocation: "
						+ command.player + " " + group);
			} else {
				putHouses(group);
				finishEndOfTurn();
			}
			break;
		case CREATE_AUCTION:
			Assert.assertTrue(currentState.turnState == TurnState.END_OF_TURN);
			Assert.assertNotNull(command.property);
			Assert.assertEquals(
					"Invalid auction choice: " + command.toString(),
					query.getOwner(currentState,
							board.properties.get(command.property)),
					command.player);
			currentState.turnState = TurnState.AUCTION;
			currentState.auctionState = new LandsharkAuctionState()
					.setProperty(command.property).setAuctionOwner(
							command.player);
			snapshot(command.player + " started an auction for "
					+ command.property);
			break;
		case PASS_TURN:
			snapshot(command.player + " passes the end-of-turn action");
			finishEndOfTurn();
			break;
		}

		updateGame();
		return true;
	}

	private void putHouses(String group) {
		Assert.assertTrue(query.canPutHouses(currentState,
				query.getCurrentPlayer(currentState), group));
		Integer pastHouses = currentState.groupHouseCount.get(group);
		if (pastHouses == null) {
			pastHouses = 0;
		}
		currentState.groupHouseCount.put(group, pastHouses + 1);

		// TODO: The fact that you buy houses later is neglected here. Maybe
		// create a new entry?
		int cost = query.getHousePrice(currentState, group);
		query.getCurrentPlayerState(currentState).cash -= cost;
		Assert.assertTrue(query.getCurrentPlayerState(currentState).cash >= 0);

		for (String property : board.propertyGroups.get(group).memberNames) {
			List<PropertyStats> propertyOwnerStats = currentState.stats.propertyOwnerStats
					.get(property);
			PropertyStats ownerStats = propertyOwnerStats
					.get(propertyOwnerStats.size() - 1);
			int perPropertyCost = cost
					/ board.propertyGroups.get(group).memberNames.size();
			ownerStats.investment = ownerStats.investment + perPropertyCost;
		}

		snapshot(query.getCurrentPlayerState(currentState).name
				+ " bought houses on his " + group + " properties for " + cost);
	}

	private void completeAuction() {
		LandsharkAuctionState auctionState = currentState.auctionState;
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
						if (currentState.playerStates.get(onPlayer).name
								.equals(entry.getKey())) {
							// The current highest bidder loses based on turn
							// order
							highestBidder = entry.getKey();
							break;
						}
						onPlayer = (onPlayer + 1)
								% currentState.playerStates.size();
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
		int openingBid = query
				.getGroupForProperty(currentState.auctionState.property).openingBid;
		if (winningBid < openingBid) {
			// No one bid a positive number, skip the auction
			message = "No one bid on the auction.  Auction cancelled.";
		} else {
			if (auctionState.auctionOwner == null
					|| highestBidder.equals(auctionState.auctionOwner)) {
				// The winner has to pay the bank
				highestBidderState.cash -= winningBid;
				message = highestBidder + " won the auction and pays "
						+ winningBid + " to the bank";
			} else {
				// The winner pays someone else
				highestBidderState.cash -= winningBid;
				PlayerState seller = query.getPlayerState(currentState,
						auctionState.auctionOwner);
				seller.cash += winningBid;
				seller.properties.remove(auctionState.property);
				PropertyStats propertyStats = getLatestPropertyStats(auctionState.property);
				propertyStats.revenue += winningBid;
				message = highestBidder + " won the auction and pays "
						+ winningBid + " to " + seller.name;
			}
			highestBidderState.properties
					.add(currentState.auctionState.property);
			boolean winningCreatesGroup = query.playerOwnsGroup(currentState,
					highestBidder, auctionProperty.group);
			currentState.stats.propertyOwnerStats.get(auctionState.property)
					.add(new PropertyStats(highestBidder, winningBid, 0, 0,
							winningCreatesGroup, 1));
		}
		currentState.turnState = TurnState.AUCTION_RESULTS;
		snapshot(message);
		currentState.auctionState = null;
		finishEndOfTurn();
	}

	private PropertyStats getLatestPropertyStats(String property) {
		List<PropertyStats> statsList = currentState.stats.propertyOwnerStats
				.get(property);
		Assert.assertFalse(statsList.isEmpty());
		return statsList.get(statsList.size() - 1);
	}

	private void beginNewTurn() {
		PlayerState currentPlayerState = query
				.getCurrentPlayerState(currentState);
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
			snapshot(currentPlayerState.name
					+ " draws a dividend card and collects "
					+ (DIVIDEND_PAYOUT * 2) + ".  Everyone else collects "
					+ DIVIDEND_PAYOUT + ".");
			finishAction();
			break;
		case PROPERTY: {
			Property property = board.properties.get(actionName);
			String owner = query.getOwner(currentState, property);
			if (owner != null
					&& owner.equals(query.getCurrentPlayer(currentState))) {
				// Landed on one's own property, do nothing
				currentState.turnState = TurnState.LANDED_ON_SELF;
				snapshot(currentPlayerState.name
						+ " landed on his/her own property");
				finishAction();
			} else if (owner != null) {
				// Pay owner
				int rent = query.getRent(currentState, property);
				currentPlayerState.cash -= rent;
				query.getPlayerState(currentState, owner).cash += rent;
				getLatestPropertyStats(property.name).revenue += rent;
				currentState.turnState = TurnState.PAYING_RENT;
				snapshot(currentPlayerState.name + " pays " + rent + " to "
						+ owner + " for rent on " + property.name);
				finishAction();
			} else {
				// Make sure someone can afford the base price
				if (!query.someoneCanAfford(currentState,
						board.propertyGroups.get(property.group).openingBid)) {
					currentState.turnState = TurnState.AUCTION;
					snapshot("No one can afford the auction.");
					finishAction();
				} else {
					// Start auction
					currentState.turnState = TurnState.AUCTION;
					currentState.auctionState = new LandsharkAuctionState()
							.setProperty(property.name);
					snapshot(currentPlayerState.name
							+ " starts an auction for " + property.name);
				}
			}
			break;
		}
		case TAXES:
			currentPlayerState.cash -= DIVIDEND_PAYOUT / 4;
			for (PlayerState playerState : currentState.playerStates) {
				playerState.cash -= DIVIDEND_PAYOUT / 4;
			}
			currentState.turnState = TurnState.PAYING_TAX;
			snapshot(currentPlayerState.name + " draws a taxes and loses "
					+ DIVIDEND_PAYOUT / 2 + ".  Everyone else loses "
					+ (DIVIDEND_PAYOUT / 4) + ".");
			finishAction();
			break;
		default:
			break;

		}
	}

	private boolean handlePlayerDeath(PlayerState playerState, String reason) {
		if (playerState.quit || playerState.cash < 0) {
			playerState.properties.clear();
			snapshot(playerState.name + " is removed from the game.  Reason: "
					+ reason + ".");
			playerState.active = false;
			return true;
		}
		return false;
	}

	private void finishAction() {
		// Handle player death (if appropriate)
		for (PlayerState playerState : currentState.playerStates) {
			if (query.isActivePlayer(playerState)) {
				if (handlePlayerDeath(playerState, "bankrupt")) {
				}
			}
		}

		if (!query.isActivePlayer(query.getCurrentPlayerState(currentState))) {
			finishEndOfTurn();
			return;
		}

		// If we can take an end-of-turn action, set the state so the user can
		// opt
		// to take an action.
		if (!query.getCurrentPlayerState(currentState).properties.isEmpty()) {
			currentState.turnState = TurnState.END_OF_TURN;
			snapshot(query.getCurrentPlayer(currentState)
					+ " can take an end-of-turn action.");
			return;
		} else {
			finishEndOfTurn();
		}
	}

	private void finishEndOfTurn() {
		if (isGameOver()) {
			// Don't bother starting a new turn if the game is over.
			return;
		}

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
		currentState.serializedRng = B64Utils.objectToString(rng);
		currentState.message = message;
		game.states.add(currentState.deepCopy());
		System.out.println(message);
		System.out.println(query.getCurrentPlayerState(currentState));
		currentState.message = null;
		currentState.serializedRng = null;
	}

	private void updateGame() {
		// Add the state to the history
		JacksonDBCollection<LandsharkGame, String> gameCollection = ThriftMongoJackService
				.instance().getCollection(LandsharkGame.class);
		gameCollection.updateById(game._id, game);
	}

	@Override
	protected boolean isGameOver() {
		return query.getActivePlayers(currentState) <= 1;
	}

	public static void main(String[] args) throws IOException, TException {
		MongoClient mongoClient = new MongoClient("localhost", 27017);
		ThriftMongoJackService dbService = ThriftMongoJackService.initialize(
				mongoClient, "boardgamehub",
				Lists.<Class<?>>newArrayList(LandsharkBox.class, LandsharkGame.class));
		JacksonDBCollection<LandsharkBox, String> boxCollection = dbService
				.getCollection(LandsharkBox.class);
		LandsharkBox board = boxCollection.find()
				.sort(DBSort.desc("timestamp")).next();

		List<String> playerNames = new ArrayList<String>();
		playerNames.add("a");
		playerNames.add("b");
		// playerNames.add("c");
		// playerNames.add("d");
		LandsharkGame game = new LandsharkGame().setPlayers(playerNames);
		game = dbService.getCollection(LandsharkGame.class).insert(game)
				.getSavedObject();
		LandSharkEngine engine = new LandSharkEngine(new Random(1L), board,
				game);
		engine.initialize();

		Map<String, AbstractPlayer> playerControllers = new HashMap<>();
		// playerControllers.put("a", new ConsolePlayer("a"));
		playerControllers.put("a", new SimplePlayer("a"));
		playerControllers.put("b", new SimplePlayer("b"));
		// playerControllers.put("c", new SimplePlayer("c"));
		// playerControllers.put("d", new SimplePlayer("d"));
		engine.game.started = true;

		while (!engine.isGameOver()) {
			// Poll for inputs
			for (AbstractPlayer playerController : playerControllers.values()) {
				if (!engine.query.isActivePlayer(engine.query.getPlayerState(
						engine.currentState, playerController.getName()))) {
					// Player is not active
					continue;
				}

				if (engine.currentState.turnState != TurnState.AUCTION
						&& !engine.query.getCurrentPlayer(engine.currentState)
								.equals(playerController.getName())) {
					// It isn't this player's turn.
					continue;
				}

				LandsharkCommand command = playerController.fetchCommand(
						engine.query, engine.currentState);
				System.out.println("Polling for inputs: " + command);
				if (command == null) {
					continue;
				}
				if (command.type == LandsharkCommandType.LEAVE_GAME) {
					System.out.println("ABORTING GAME");
					// Special command to abort
					break;
				}
				if (engine.processInput(command)) {
					break;
				}
			}
		}
		System.out.println("GAME OVER");
		System.out.println(engine.currentState);
	}
}
