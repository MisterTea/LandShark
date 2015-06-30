package com.github.mistertea.boardgame.landshark.player;

import java.util.HashMap;
import java.util.Scanner;

import com.github.mistertea.boardgame.landshark.LandsharkCommand;
import com.github.mistertea.boardgame.landshark.LandsharkCommandType;
import com.github.mistertea.boardgame.landshark.LandsharkState;
import com.github.mistertea.boardgame.landshark.StateQuery;

public class ConsolePlayer extends AbstractPlayer {
  private Scanner s;

  public ConsolePlayer(String name) {
    super(name);
    s = new Scanner(System.in);
  }

  @Override
  public LandsharkCommand fetchCommand(StateQuery query, LandsharkState state) {
    System.out.println("INPUT COMMAND FOR PLAYER " + name);
    String input = s.nextLine();
    System.out.println("GOT INPUT: " + input);
    String tokens[] = input.split(" ");
    if (tokens.length == 0) {
      return null;
    }
    LandsharkCommandType type = null;
    for (LandsharkCommandType checkType : LandsharkCommandType.values()) {
      if (checkType.name().equalsIgnoreCase(tokens[0])) {
        type = checkType;
        break;
      }
    }
    System.out.println("INPUT TYPE: " + type);
    if (type == null) {
      return null;
    }
    // in-game command
    String purchaseHouseGroup = null;
    LandsharkCommandType landsharkType = type;
    if (landsharkType == LandsharkCommandType.BUY_HOUSES) {
      purchaseHouseGroup = tokens[1];
    }
    int bid = 0;
    if (landsharkType == LandsharkCommandType.BID_AUCTION) {
      bid = Integer.parseInt(tokens[1]);
    }
    String property = null;
    if (tokens.length > 2) {
      property = input.substring(input.indexOf(tokens[2]));
    }
    LandsharkCommand command = new LandsharkCommand(name,
        System.currentTimeMillis(), type, property,
        purchaseHouseGroup, bid, "");
    return command;
  }

}
