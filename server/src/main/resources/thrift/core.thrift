namespace java com.github.mistertea.boardgame.core
namespace scala com.github.mistertea.boardgame.core.scala

struct DieRoll {
  1:i32 numDice,
  2:i32 dieSize,
  3:i32 modifier,
}

enum CoreCommandType {
  CHAT,
  QUIT,
}

struct CoreCommand {
  1:string player,
  2:i64 creationTime,
  3:i32 type,
  4:string chat,
}

enum ServerMessageType {
  ERROR,
  CHAT,
  NEW_STATE,
}

struct ServerMessage {
  1:ServerMessageType type,
  2:i64 creationTime,
  3:string message,
}

struct PlayerServerState {
  1:string player,
  2:string ipAddress,
  3:string aiType,
}
