namespace java com.github.mistertea.boardgame.landshark

include "core.thrift"

enum TurnState {
  // Beginning of the game
  START_GAME,

  // Drawing an action card
  DRAWING_ACTION,

  // Drew a property that hte player already owns
  LANDED_ON_SELF,

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
struct PropertyGroup {
  1:set<string> memberNames = [],
  2:list<i32> rent = [],
  3:i32 housePrice,
  4:i32 openingBid,
  5:string name
}

struct PlayerState {
  1:string name,
  2:set<string> properties = [],
  4:i32 cash = 0,
  5:bool quit=false,
  6:bool active=true,
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

struct LandsharkBox {
  1:string _id,
  2:i64 timestamp,
  3:map<string,Action> actions = {},
  4:map<string,Property> properties = {},
  5:i32 startingMoney = 1500,
  6:map<string,PropertyGroup> propertyGroups = {},
}


struct LandsharkAuctionState {
  1:string property,
  2:string auctionOwner, // null means the bank owns the property
  3:map<string, i32> bids = {},
  4:bool forcedAuction = false
}

struct LandsharkState {
  1:string _id,
  2:list<PlayerState> playerStates = [],
  3:map<string, i32> groupHouseCount = {},
  4:i32 playerTurn = 0,
  5:TurnState turnState = TurnState.START_GAME,
  6:LandsharkAuctionState auctionState,
  7:list<string> actionCardOrder = [],
  8:i32 nextActionCard = 0,
  9:string serializedRng,
  10:string gameId;

  100:string message,
  101:Stats stats;
  102:i64 timestamp;
}

enum LandsharkCommandType {
  JOIN_GAME,
  OBSERVE_GAME,
  LEAVE_GAME,
  CHAT,

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
  2:i64 timestamp,
  3:LandsharkCommandType type,
  4:string property,
  5:string housePurchaseGroup,
  6:i32 bid,
  7:string chatMessage
}

struct LandsharkGame {
    1:string _id,
    2:list<string> players = [],
    3:list<string> observers = [],
    4:string boxId,
    5:list<LandsharkCommand> commands = [],
    6:list<LandsharkState> states = [],
    7:bool started = false,
}
