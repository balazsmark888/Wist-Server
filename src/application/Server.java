package application;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.SelectionModel;
import javafx.scene.control.Tab;
import javafx.scene.input.MouseEvent;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;

public class Server extends Controller {
    /**
     * Contains the UI's server part of the controller
     * */
    protected ServerSocket server;
    protected boolean inGame = false;

    @FXML
    private void startServerButtonClicked(MouseEvent me) {
	/**
	 * "Start server" button's "Mouse clicked" event handler
	 * Starts server setup
	 * Starts connection thread
	 * Disables the "Start server" button and "Number of players" field
	 * */
	if (numberOfPlayersField.getText().matches("[2-6]")) {
	    setupServer();
	    currentRound.setDealer(0);
	    currentRound.setTurn((currentRound.getDealer() + 1) % currentRound.getNumberOfPlayers());
	    currentRound.setPredictionPhase(true);
	    waitForConnections();
	    startServerButton.setDisable(true);
	    numberOfPlayersField.setDisable(true);
	} else {
	    alert("The number of players must be a whole number between 2 and 6!");
	    numberOfPlayersField.clear();
	}
    }

    @FXML
    protected void startGameButtonClicked(MouseEvent me) {
	/**
	 * "Start game" button's "Mouse clicked" event handler
	 * Starts first round
	 * */
	inGame = true;
	currentRound.nextRound();
    }

    private void setupServer() {
	/**
	 * Starts the serversocket
	 * sets the numberOfPlayers variable's value
	 * */
	try {
	    server = new ServerSocket(4444, 5);
	} catch (IOException e) {
	    e.printStackTrace();
	}
	currentRound.setNumberOfPlayers(Integer.parseInt(numberOfPlayersField.getText()));

    }

    private void waitForConnections() {
	/**
	 * Starts a thread which is responsible for accepting the clients' requests
	 * */
	Thread thread = new Thread(new Runnable() {

	    @Override
	    public void run() {
		for (int i = 0; i < currentRound.getNumberOfPlayers(); i++) {
		    try {
			Socket connection = server.accept();
			setupStreams(connection, i);
		    } catch (SocketException socketException) {

		    } catch (IOException e) {
			e.printStackTrace();
		    }
		}
		startGameButton.setDisable(false);
	    }
	});
	thread.setDaemon(true);
	thread.start();
    }

    private void setupStreams(final Socket connection, final int index) {
	/**
	 * Sets up the input and output streams from the given connection
	 * Creates a new Player object with, sets up it's ClientInfo
	 * */
	Platform.runLater(new Runnable() {
	    @Override
	    public void run() {
		try {
		    ObjectOutputStream out = new ObjectOutputStream(connection.getOutputStream());
		    out.flush();
		    ObjectInputStream in = new ObjectInputStream(connection.getInputStream());
		    ClientInfo info = new ClientInfo();
		    info.setConnection(connection);
		    info.setInput(in);
		    info.setOutput(out);
		    Player player = new Player();
		    player.setClientInfo(info);
		    player.setPlayerNumber(index);
		    currentRound.getPlayers().add(player);
		    createClientInputThread(currentRound.getPlayers().get(currentRound.getPlayers().size() - 1),
			    currentRound.getPlayers().size() - 1);
		    createClientOutputThread(currentRound.getPlayers().get(currentRound.getPlayers().size() - 1),
			    currentRound.getPlayers().size() - 1);
		    playersConnectedList.getItems().add(connection.getInetAddress().getHostName());
		} catch (IOException ioException) {
		    ioException.printStackTrace();
		}
	    }
	});
    }

    private void createClientOutputThread(Player player, int playerIndex) {
	/**
	 * Creates a thread wich is responsible for sending out packages for the
	 * Client specified by the parameters
	 * It sends packages twice per second
	 * */
	Thread thread = new Thread(new Runnable() {

	    @Override
	    public void run() {
		try {
		    player.getClientInfo().getOutput().writeObject(playerIndex);
		    player.getClientInfo().getOutput().flush();

		} catch (Exception e) {
		    e.printStackTrace();
		}

		while (!player.getClientInfo().getConnection().isClosed()) {
		    try {
			Thread.sleep(500);
			player.getClientInfo().getOutput().writeObject(currentRound.createClientPackage(playerIndex));
			player.getClientInfo().getOutput().flush();
		    } catch (InterruptedException interruptedException) {
			interruptedException.printStackTrace();
		    } catch (SocketException socketException) {
			socketException.printStackTrace();
		    } catch (IOException ioException) {
			ioException.printStackTrace();
		    } 
		}
	    }
	});
	thread.setDaemon(true);
	thread.start();

    }

    private void createClientInputThread(Player player, int playerIndex) {
	/**
	 * Creates a thread which is responsible for continuously reading the clients' inputs
	 * */
	Thread thread = new Thread(new Runnable() {

	    @Override
	    public void run() {
		try {
		    String name = player.getClientInfo().getInput().readObject().toString();
		    player.setName(name);

		    while (!player.getClientInfo().getConnection().isClosed()) {
			Integer answer = (Integer) player.getClientInfo().getInput().readObject();
			if (currentRound.getTurn() == player.getPlayerNumber() && !currentRound.isWaitingStage()) {
			    if (currentRound.isPickPhase()) {
				processPickPhaseInput(player, answer);
			    } else if (currentRound.isPredictionPhase()) {
				processPredictionPhaseInput(player, answer);
			    }

			} else if (currentRound.isWaitingStage() && !player.isWaitingChecked()) {
			    inWaitingStage(playerIndex);
			}
		    }
		} catch (ClassNotFoundException classNotFoundException) {
		    classNotFoundException.printStackTrace();
		} catch (IOException ioException) {
		    ioException.printStackTrace();
		} finally {
		    closeServer();
		}
	    }
	});
	thread.setDaemon(true);
	thread.start();
    }

    protected void processPickPhaseInput(Player player, int answer) {
	/**
	 * If the client input happened during Pick Phase, checks the validity of the answer
	 * */
	if (currentRound.getTurnCounter() != 0) {
	    if (player.hasColor(currentRound.getMinorRound().getFirstCardColor())
		    && player.getCards().get(answer).getColor() != currentRound.getMinorRound().getFirstCardColor()) {

	    } else if (!player.hasColor(currentRound.getMinorRound().getFirstCardColor())
		    && player.hasColor(currentRound.getTromph().getColor())
		    && player.getCards().get(answer).getColor() != currentRound.getTromph().getColor()) {

	    } else {
		nextStep(answer);
	    }
	} else {
	    nextStep(answer);
	}
    }

    protected void processPredictionPhaseInput(Player player, int answer) {
	/**
	 * If the client input happened during Prediction Phase
	 * */
	nextStep(answer);
    }

    public void closeServer() {
	/**
	 * Closes the connections and streams from the server side and then closes the server itself
	 * */
	try {
	    for (Player p : currentRound.getPlayers()) {
		p.getClientInfo().close();
	    }
	    server.close();
	} catch (IOException ioException) {
	    ioException.printStackTrace();
	}
    }

    protected void waitingStageOver() {
	/**
	 * If the match is not over, it sets the next minor round's variables, otherwise it closes the server
	 * */
	if (currentRound.getRoundNumber() < 3 * currentRound.getNumberOfPlayers() + 12) {
	    currentRound.getPlayers().get(currentRound.getMinorRound().winner())
		    .setWon(currentRound.getPlayers().get(currentRound.getMinorRound().winner()).getWon() + 1);
	    currentRound.setTurn(currentRound.getMinorRound().winner());
	    currentRound.getMinorRound().getPlayedCards().clear();
	    currentRound.getMinorRound().setFirstCardColor(-1);
	    currentRound.setMinorTurn((currentRound.getMinorTurn() + 1) % currentRound.getNumberOfCards());
	    if (currentRound.getMinorTurn() == 0 && currentRound.getTurnCounter() == 0) {
		roundOver();
	    }
	} else {
	    closeServer();
	    stage.close();
	}
    }

    protected void inWaitingStage(int playerIndex) {
	/**
	 * Processes client input during Waiting Stage
	 * */
	currentRound.getPlayers().get(playerIndex).setWaitingChecked(true);
	if (currentRound.isAllWaitingsChecked()) {
	    currentRound.setWaitingStage(false);
	    waitingStageOver();
	}
    }

    @FXML
    protected void cancelGameButtonClicked(MouseEvent me) {
	/**
	 * "Cancel game" button's "Mouse Clicked" event handler
	 * Closes the connection, server and the window
	 * */
	closeServer();
	stage.close();
    }

}
