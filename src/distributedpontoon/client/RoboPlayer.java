package distributedpontoon.client;

import distributedpontoon.shared.Hand;
import distributedpontoon.shared.IClientGame;
import distributedpontoon.shared.NetMessage;
import distributedpontoon.shared.NetMessage.MessageType;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Random;

/**
 * An automated Pontoon player. This player will use the same tactic each game, 
 * calling {@link IClientGame#twist()} as long as the current value of the 
 * {@link Hand} is below a randomised threshold value (1 to 21 inclusive).
 * 
 * @author 6266215
 */
public class RoboPlayer implements IPlayer
{
    /** The threshold for twisting for this {@link RoboPlayer}. */
    private final int threshold;
    /** A mapping of {@link IClientGame}s to their executing {@link Thread}s. */
    private final HashMap<IClientGame, Thread> games;
    /** A mapping of {@link IClientGame}s to the player ID for each game. */
    private final HashMap<IClientGame, Integer> playerIDs;
    /** The remaining credits this {@link RoboPlayer} can bet with.  */
    private int balance;
    /** Indicates whether or not this {@link RoboPlayer} is still playing. */
    private boolean playing;
    
    /**
     * Creates a new {@link RoboPlayer} with a randomised threshold value and 
     * no games assigned.
     * 
     * @since 1.0
     */
    public RoboPlayer()
    {
        Random randomiser = new Random();
        this.threshold = randomiser.nextInt(21);
        this.games = new HashMap<>();
        this.playerIDs = new HashMap<>();
        this.balance = 300;
    }
    
    @Override
    public void init()
    {
        HashMap<String, Integer> servers = new HashMap<>();
        String serverName = "CMPLAB2-04";
        int directoryPort = 55552;
        Socket directorySocket;
        try {
            InetAddress address = InetAddress.getByName(serverName);
            directorySocket = new Socket(address, directoryPort);
            ObjectOutputStream output = new ObjectOutputStream(directorySocket.getOutputStream());
            ObjectInputStream input = new ObjectInputStream(directorySocket.getInputStream());
            
            output.writeObject(MessageType.QUERY_SERVERS);
            output.flush();
            MessageType reply = (MessageType)input.readObject();
            if (reply == MessageType.QUERY_SERVERS) {
                servers = (HashMap)input.readObject();
            }
        } catch (UnknownHostException hostEx) {
            System.err.println(hostEx.getMessage());
        } catch (IOException ioEx) {
            System.err.println(ioEx.getMessage());
        } catch (ClassNotFoundException cnfEx) {
            System.err.println(cnfEx.getMessage());
        }
        //String[] addresses = new String[]{"CMPLAB2-04", "CMPLAB2-03", "CMPLAB1-15"};
        for (String address : servers.keySet()) {
            int tmpPort = servers.get(address);
            IClientGame game = new ClientGame(this, 50, address, tmpPort);
            Thread t = new Thread(game);
            games.put(game, t);
            playerIDs.put(game, -1);
        }
        startGame();
        playing = true;
    }

    @Override
    public void setPlayerID(IClientGame game, int id)
    {
        if (playerIDs.containsKey(game))
            playerIDs.put(game, id);
    }
    
    @Override
    public void reigsterGame(IClientGame game)
    {
        Thread gameThread = new Thread(game);
        games.put(game, gameThread);
    }

    @Override
    public synchronized boolean isPlaying() { return playing; }
    
    @Override
    public void startGame()
    {
        System.out.printf("RoboPlayer with threshold %d started.\n", threshold);
        for (IClientGame g : games.keySet())
            games.get(g).start();
    }

    @Override
    public void play(IClientGame caller)
    {
        if (caller.getHand().total() < threshold) {
            caller.twist();
        } else {
            caller.stand();
        }
    }    

    @Override
    public synchronized void setBalance(int bal) { this.balance = bal; }

    @Override
    public synchronized boolean adjustBalance(int deltaBal)
    {
        balance += deltaBal;
        return balance > 0;
    }

    @Override
    public synchronized int getBalance() { return balance; }
    
    @Override
    public synchronized void playerWin(IClientGame game, boolean pontoon) 
    {
        if (pontoon) {
            System.out.printf("Player won with a pontoon! Added %d credits.\n", 
                    game.getBet());
        } else {
            System.out.printf("Player won! Bet of %d returned.\n",
                    game.getBet());
        }
        System.out.printf("Current balance: %d\n", balance);
    }

    @Override
    public synchronized void dealerWin(IClientGame game)
    { 
        System.out.printf("Dealer won. Removed %d credits.\n", game.getBet());
        System.out.printf("Current balance: %d\n", balance);
    }
    
    @Override
    public synchronized void leaveGame(IClientGame game)
    {
        game.disconnect();
        try {
            Thread t = games.remove(game);
            t.join(1000);
        } catch (InterruptedException ex) {
            System.out.println(ex.getMessage());
        }
        
        if (games.isEmpty())
            playing = false;
    }
}
