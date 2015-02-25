package com.github.mistertea.boardgame.landshark.player;

import java.util.HashMap;
import java.util.Scanner;

import com.github.mistertea.boardgame.core.CoreCommand;
import com.github.mistertea.boardgame.core.CoreCommandType;
import com.github.mistertea.boardgame.core.ThriftB64Utils;
import com.github.mistertea.boardgame.landshark.LandsharkCommand;
import com.github.mistertea.boardgame.landshark.LandsharkCommandType;
import com.github.mistertea.boardgame.landshark.State;
import com.github.mistertea.boardgame.landshark.StateQuery;

public class ConsolePlayer extends AbstractPlayer {
	private Scanner s;

	public ConsolePlayer(String name) {
		super(name);
		s = new Scanner(System.in);
	}

	@Override
	public String fetchCommand(StateQuery query, State state) {
		System.out.println("INPUT COMMAND FOR PLAYER " + name);
		String input = s.nextLine();
		System.out.println("GOT INPUT: " + input);
		String tokens[] = input.split(" ");
		if (tokens.length == 0) {
			return "";
		}
		int type = -1;
		for (LandsharkCommandType checkType : LandsharkCommandType.values()) {
			if (checkType.name().equalsIgnoreCase(tokens[0])) {
				type = checkType.getValue();
				break;
			}
		}
		for (CoreCommandType checkType : CoreCommandType.values()) {
			if (checkType.name().equalsIgnoreCase(tokens[0])) {
				type = checkType.getValue();
				break;
			}
		}
		System.out.println("INPUT TYPE: " + type);
		if (type < 0) {
			return "";
		}
		if (type < 1000) {
			// Core command
			CoreCommand command = new CoreCommand(name,
					System.currentTimeMillis(), type, null);
			return ThriftB64Utils.ThriftToString(command);
		} else {
			// in-game command
			int houses = 0;
			LandsharkCommandType landsharkType = LandsharkCommandType
					.findByValue(type);
			if (landsharkType == LandsharkCommandType.BUY_HOUSES) {
				houses = Integer.parseInt(tokens[1]);
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
					new HashMap<String, Integer>(), bid);
			return ThriftB64Utils.ThriftToString(command);
		}
	}

}
