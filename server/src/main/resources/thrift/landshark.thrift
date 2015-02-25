namespace java com.github.mistertea.boardgame.landshark
namespace scala com.github.mistertea.boardgame.landshark.scala

include "core.thrift"

enum TurnState {
  // Beginning of the game
  START_GAME,
  
  // Drawing an action card
  DRAWING_ACTION,

  // Pay rent if landing on an opponent's property
  PAYING_RENT,
  
  // Everyone pays tax when someone draws tax card
  PAYING_TAX,

  // Everyone collects money when dividend card is drawn
  COLLECTING_DIVIDEND,

  // An auction is ongoing
  AUCTION,
  
  // At the end of the turn, the player can start an auction or buy houses
  END_OF_TURN,
  
  // Houses are being bought
  BUYING_HOUSES,
  
  // Auction results are being presented
  AUCTION_RESULTS,
  
  // A player has gone into backruptcy
  PLAYER_BANKRUPTED,
  
  // The game has ended
  GAME_OVER,
}

struct PropertyGroup {
  1:set<string> memberNames = [],
  2:list<i32> rent = [],
  3:i32 housePrice,
}

struct PlayerState {
  1:string name,
  2:set<string> properties = [],
  4:i32 cash = 0,
  5:bool quit=false,
}

struct AuctionState {
  1:string property,
  2:string auctionOwner, // null means the bank owns the property
  3:map<string, i32> bids = {},
  4:bool forcedAuction = false
}

struct State {
  1:string id,
  2:list<PlayerState> playerStates = [],
  3:map<string, i32> groupHouseCount = {},
  4:i32 playerTurn = 0,
  5:TurnState turnState = TurnState.START_GAME,
  6:AuctionState auctionState,
  7:list<string> actionCardOrder = [],
  8:i32 nextActionCard = 0,
  
  100:string message,
}

enum ActionType {
  PROPERTY,
  DIVIDEND,
  TAXES,
}

struct Action {
  1:string name,
  2:ActionType type,
}


struct Property {
  1:string name,
  2:string group,
}

struct GameBox {
  1:map<string,Action> actions = {},
  2:map<string,Property> properties = {},
  4:i32 startingMoney = 1500,
  5:map<string,PropertyGroup> propertyGroups = {},
}

enum LandsharkCommandType {
  // Place a bid in an auction
  BID_AUCTION = 1000,
  
  // Pass on an auction
  PASS_AUCTION,
  
  // Buy houses
  BUY_HOUSES,
  
  // Create a new auction
  CREATE_AUCTION,
  
  // Skip buying houses/creating auctions
  PASS_TURN,
}

struct LandsharkCommand {
  1:string player,
  2:i64 creationTime,
  3:i32 type,
  4:string property,
  5:map<string, i32> housePurchases,
  6:i32 bid,
}

struct PropertyStats {
  1:string owner,
  2:i32 price,
  3:i32 investment,  // investment includes buying houses
  4:i32 revenue,
  5:bool street,  // True if the owner owns the entire street
  6:i32 duration,
}

struct PlayerStats {
  1:string name,
}

struct Stats {
  1:map<string, list<PropertyStats> > propertyOwnerStats = {},
  2:map<string, PlayerStats> playerStats = {},
}
