package com.github.mistertea.boardgame.landshark;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import junit.framework.Assert;

import org.apache.thrift.TException;
import org.apache.thrift.protocol.TJSONProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.transport.TIOStreamTransport;
import org.apache.thrift.transport.TTransport;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.mongojack.DBSort;
import org.mongojack.JacksonDBCollection;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.github.mistertea.boardgame.core.MongoJackService;
import com.google.common.collect.Lists;

public class LandsharkBoardGenerator {

  /**
   * @param args
   * @throws TException
   * @throws IOException 
   * @throws JsonMappingException 
   * @throws JsonGenerationException 
   */
  public static void main(String[] args) throws TException, JsonGenerationException, JsonMappingException, IOException {
    LandsharkBox board = new LandsharkBox().setTimestamp(new DateTime(DateTimeZone.UTC).getMillis());
    addAction(board, "Tourist Season", ActionType.DIVIDEND);
    addAction(board, "Christmas Vacation", ActionType.DIVIDEND);
    addAction(board, "Spring Break", ActionType.DIVIDEND);

    addAction(board, "Payroll Tax", ActionType.TAXES);
    addAction(board, "Property Repairs", ActionType.TAXES);
    addAction(board, "Legal Battles", ActionType.TAXES);

    //addAction(board, "Bonhams", ActionType.AUCTIONHOUSE);
    //addAction(board, "Antiquorum", ActionType.AUCTIONHOUSE);
    //addAction(board, "Dorotheum", ActionType.AUCTIONHOUSE);

    addStreet(board, Lists.newArrayList("Male - Maldives",
        "Mpumalanga - South Africa", "Saint Elizabeth Parish - Jamaica"), 60, 4,
        150, "Brown");

    addStreet(board, Lists.newArrayList("Kamalame Cay - The Bahamas",
        "Siargao  - Philippines", "Budva - Montenegro"), 100, 8, 150, "LightBlue");

    addStreet(board, Lists.newArrayList("St Lucia - West Indies",
        "Ambergris Caye - Belize", "Sanya - China"), 150, 12, 300, "Purple");

    addStreet(board, Lists.newArrayList("Fernando de Noronha - Brazil",
        "Valparaíso - Chile", "Santorini - Greece"), 200, 16, 300, "Orange");

    addStreet(board, Lists.newArrayList("Bora Bora - French Polynesia",
        "Peter Island - British Virgin Islands", "Costa Alegre - Mexico"), 225, 20,
        450, "Red");

    addStreet(board, Lists.newArrayList("Phuket - Thailand",
        "Naples - Florida, USA", "Eze - France"), 275, 24, 450, "Yellow");

    addStreet(board, Lists.newArrayList("Dubai - United Arab Emirates",
        "Banff - Canada", "Capri - Italy"), 300, 24, 600, "Green");

    addStreet(board, Lists.newArrayList("Laguna Beach - California, USA",
        "Kauai - Hawaii", "Monte Carlo - Monaco"), 375, 40, 600, "DarkBlue");

    TTransport trans = new TIOStreamTransport(new FileOutputStream("Board.tft"));
    TProtocol prot = new TJSONProtocol(trans);
    board.write(prot);
    trans.close();
    
    MongoJackService dbService = MongoJackService.instance();
    JacksonDBCollection<LandsharkBox, String> boxCollection = dbService.getCollection(LandsharkBox.class);
    board = boxCollection.insert(board).getSavedObject();
    System.out.println(board._id);
    
    LandsharkBox board2 = boxCollection.findOneById(board._id);
    if (!board.equals(board2)) {
      System.out.println("" + board);
      System.out.println("" + board2);
      throw new RuntimeException("OOPS");
    }
    
    LandsharkBox board3 = boxCollection.find().sort(DBSort.desc("timestamp")).next();
    if (!board.equals(board3)) {
      System.out.println("" + board);
      System.out.println("" + board3);
      throw new RuntimeException("OOPS");
    }

    /*
     * TTransport trans2 = new TIOStreamTransport(new FileInputStream(
     * "NewBoard.tft")); TProtocol prot2 = new TJSONProtocol(trans2); Board
     * newBoard = new Board(); newBoard.read(prot2); trans2.close();
     * 
     * System.out.println(board.toString()); System.out.println("***");
     * System.out.println(newBoard.toString());
     * Assert.assertEquals(board.toString(), newBoard.toString());
     */
  }

  private static void addAction(LandsharkBox board, String name, ActionType type) {
    Action property = new Action(name, type);
    Assert.assertFalse(board.actions.containsKey(name));
    board.actions.put(name, property);
  }

  private static void addStreet(LandsharkBox board, List<String> names,
      int openingBid, int baseRent, int housePrice, String group) {
    ActionType type = ActionType.PROPERTY;
    ArrayList<Integer> rents = null;
    Integer oneHouseRent = baseRent * 5;
    rents = new ArrayList<Integer>();
    rents.add(baseRent);
    rents.add(oneHouseRent);

    int cumsum = oneHouseRent;
    cumsum *= 3; // second house payoff is 3x first house
    cumsum += 9; // ceiling function
    rents.add((cumsum / 10) * 10);

    // The payoff multiplier from 2 to 3 houses is computed based on the
    // rent.
    double twoToThree = Math.min(3.0, 4.8 * Math.pow(Math.log(baseRent), -0.6));
    cumsum *= twoToThree;
    cumsum += 19; // ceiling function
    rents.add((cumsum / 20) * 20);

    // The payoff from 3 to 4 houses follows a log-power curve
    double threeToFour = Math.min(2.0, 2 * Math.pow(Math.log(baseRent), -0.4));
    cumsum *= threeToFour;
    cumsum += 49; // ceiling function
    rents.add((cumsum / 50) * 50);

    double fourToHotel = Math
        .min(1.5, 1.5 * Math.pow(Math.log(baseRent), -0.2));
    cumsum *= fourToHotel;
    cumsum += 49; // ceiling function
    rents.add((cumsum / 50) * 50);

    System.out.print("RENTS: ");
    for (int r : rents) {
      System.out.print(r + " ");
    }
    System.out.println();

    Assert.assertFalse(board.propertyGroups.containsKey(group));
    board.propertyGroups.put(group,
        new PropertyGroup().setOpeningBid(openingBid).setHousePrice(housePrice).setRent(rents));
    for (String name : names) {
      Action action = new Action(name, type);
      Property property = new Property(name, group);
      Assert.assertFalse(board.actions.containsKey(name));
      board.actions.put(name, action);
      board.properties.put(name, property);
      board.propertyGroups.get(group).memberNames.add(name);
    }
  }
}
