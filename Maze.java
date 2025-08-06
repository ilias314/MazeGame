import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.*;

public class Maze implements Serializable {

    //CONSTANTS
    private final int MIN_SIZE = 1;
    private final int MIN_CELL_SIZE = 10;
    
    private final int PLAYER_COLOR = 0x0000FF;    // Blue
    private final int AI_COLOR = 0xFF0000;        // Red
    private final int WALL_COLOR = 0x000000;      // Black
    private final int ENTRANCE_COLOR = 0x00FF00;  // Green
    private final int EXIT_COLOR = 0xFF0000;      // Red
    private final int TIMER_TEXT_COLOR = 0x000000;// Black
    private final int REGENERATION_TIME = 180; // seconds
    private final int PATH_COLOR = 0x00FF00; // Green path for solve/next10
    
    //FIELDS
     
    // Maze dimensions
    private int width;
    private int height;
    private int cellSize;
    
    // Maze data
    private Cell[][] grid;
    private final Random random;
    
    // Entrance and exit cells
    private Cell entrance;
    private Cell exit;
    
    // Player and AI
    private Player player;
    private Player aiPlayer;
    
    // AI solution path
    private final List<Character> aiSolution;
    private List<Character> aiPlannedMoves;
    
    // Turtle for drawing
    private transient Turtle turtle;
    
    // Game timers
    private transient Timer gameTimer;
    private  transient Timer displayTimer;
    private transient Timer regenerationTimer;
    
    // Game state
    private int elapsedTime;
    private boolean isGameRunning;
    private boolean isRegenerating;
    
    // Competitive mode
    private boolean isCompetitiveMode;
    private int humanMoveCount;
    private int aiMoveCount;
    private boolean isHumanTurn;

    // Scanner (no longer static)
    private transient Scanner scanner = new Scanner(System.in);

    //CONSTRUCTOR
    public Maze(Turtle turtle, int width, int height, int cellSize) {
        this.turtle = turtle;
        this.width = Math.max(MIN_SIZE, width);
        this.height = Math.max(MIN_SIZE, height);
        this.cellSize = Math.max(MIN_CELL_SIZE, cellSize);
        this.random = new Random();
        
        this.grid = initializeGrid();
        this.aiSolution = new ArrayList<>();
        this.aiPlannedMoves = new ArrayList<>();
        
        this.isCompetitiveMode = false;
        this.humanMoveCount = 0;
        this.aiMoveCount = 0;
        this.isRegenerating = false;
    }

    //PUBLIC METHODS
    
    //Starts a single-player game.
    public void startGame() {
        aiMoveCount = 0;
        humanMoveCount = 0;
        elapsedTime = 0;
        generate();
        draw();
        initializePlayer();
        startTimer();
        startRegenerationTimer();
        runGameLoop();
    }
    
    //Starts a competitive-mode game (player vs. AI).
    public void startCompetitiveMode() {
        humanMoveCount = 0;
        aiMoveCount = 0;
        elapsedTime = 0;
        generate();
        
        isCompetitiveMode = true;
        isHumanTurn = true;

        player = new Player(entrance);
        aiPlayer = new Player(entrance);

        aiSolution.clear();
        aiPlannedMoves.clear();

        findSolution(getCell(aiPlayer.x, aiPlayer.y), new HashSet<>(), new ArrayList<>());
        aiPlannedMoves.addAll(aiSolution);

        draw();
        redrawMazeAndPlayer();
        startTimer();
        startRegenerationTimer();
        runCompetitiveGameLoop();
    }

    //menu
    public void play() {
        System.out.println("Welcome to the Maze Game!");
        System.out.println("1) Load Last Saved Game");
        System.out.println("2) Start New Game");

        int choice = -1;
        while (choice != 1 && choice != 2) {
            System.out.print("Enter your choice (1 or 2): ");
            choice = scanner.nextInt();
            scanner.nextLine();
        }
            if (choice == 1) {
                loadGame();
                if (isGameRunning) {
                    System.out.println("Continuing the loaded game...");
                    continueGame();
                    return;
                } else {
                    System.out.println("No saved game found or error occurred. Starting a new game...");
                }
            }

            // Choose difficulty
            System.out.println("Select difficulty:");
            System.out.println("1) Easy (10x10)\n2) Medium (20x20)\n3) Hard (30x30)");
            int diffChoice = scanner.nextInt();
            scanner.nextLine();

            int size;
            switch (diffChoice) {
                case 2 -> size = 20; // Medium
                case 3 -> size = 30; // Hard
                default -> size = 10; // Easy
            }

            // We'll keep cellSize always 20px
            int cellSize = 20;

            // Re-initialize Maze with chosen settings
            this.width = size;
            this.height = size;
            this.cellSize = cellSize;
            this.grid = initializeGrid();

            // Choose mode
            System.out.println("Select game mode:");
            System.out.println("1) Single Player\n2) Competitive (vs AI)");
            int mode = scanner.nextInt();
            scanner.nextLine();

            if (mode == 2) {
                startCompetitiveMode();
            } else {
                startGame();
            }
        }


    //Resumes a loaded game
     
    public void continueGame() {
        redrawMazeAndPlayer();
        if (isCompetitiveMode) {
            runCompetitiveGameLoop();
        } else {
            runGameLoop();
        }
    }

    
     //Generates the maze (mit DFS).
     
    public void generate() {
        resetGrid();
        Cell startCell = grid[0][0];
        startCell.visited = true;

        List<Cell> stack = new ArrayList<>();
        stack.add(startCell);

        while (!stack.isEmpty()) {
            Cell current = stack.get(stack.size() - 1);
            Cell next = getUnvisitedNeighbor(current);

            if (next != null) {
                removeWalls(current, next);
                next.visited = true;
                stack.add(next);
            } else {
                stack.remove(stack.size() - 1);
            }
        }
        createEntranceAndExit();
    }

    
    //Saves the current game state to a file (savegame.dat).
     
    public void saveGame() {
        try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream("savegame.dat"))) {
            out.writeInt(this.width);
            out.writeInt(this.height);
            out.writeInt(this.cellSize);
            out.writeObject(this.grid);
            out.writeObject(this.player);
            out.writeObject(this.aiPlayer);
            out.writeObject(this.entrance);
            out.writeObject(this.exit);
            out.writeInt(this.humanMoveCount);
            out.writeInt(this.aiMoveCount);
            out.writeInt(this.elapsedTime);
            out.writeBoolean(this.isCompetitiveMode);
            out.writeBoolean(this.isHumanTurn);

        int timePassedSinceLastRegen = this.elapsedTime % REGENERATION_TIME;
        int timeUntilNextRegen = REGENERATION_TIME - timePassedSinceLastRegen;
        out.writeInt(timeUntilNextRegen);

            System.out.println("Game saved successfully.");
        } catch (IOException e) {
            System.out.println("Error saving game: " + e.getMessage());
        }
    }

    
    //Loads the game state from a file (savegame.dat).
    
    @SuppressWarnings("unchecked")
    public void loadGame() {
        try (ObjectInputStream in = new ObjectInputStream(new FileInputStream("savegame.dat"))) {
            this.width = in.readInt();
            this.height = in.readInt();
            this.cellSize = in.readInt();
            this.grid = (Cell[][]) in.readObject();
            this.player = (Player) in.readObject();
            this.aiPlayer = (Player) in.readObject();
            this.entrance = (Cell) in.readObject();
            this.exit = (Cell) in.readObject();
            this.humanMoveCount = in.readInt();
            this.aiMoveCount = in.readInt();
            this.elapsedTime = in.readInt();
            this.isCompetitiveMode = in.readBoolean();
            this.isHumanTurn = in.readBoolean();

            


            System.out.println("Game loaded successfully.");
            isGameRunning = true;

            if (isCompetitiveMode) {
                aiSolution.clear();
                aiPlannedMoves.clear();
                findSolution(getCell(aiPlayer.x, aiPlayer.y), new HashSet<>(), new ArrayList<>());
                aiPlannedMoves.addAll(aiSolution);
            }


            
            startTimer();
            int timePassedSinceLastRegen = elapsedTime % REGENERATION_TIME;
            int timeUntilNextRegen = REGENERATION_TIME - timePassedSinceLastRegen;
            startRegenerationTimer(timeUntilNextRegen);
           
            redrawMazeAndPlayer();
        } catch (IOException | ClassNotFoundException e) {
            System.out.println("Error loading game: " + e.getMessage());
        }
    }

    
     //Draws the maze (walls, entrance, exit, timer, etc.).
     
    public void draw() {
        if (turtle == null) {
            throw new IllegalStateException("Turtle not initialized");
        }
        setupTurtle();
        drawMazeStructure();
        colorEntranceAndExit();
        drawTimer();
    }

    
    public Cell getEntrance() {
        return entrance;
    }

    
    public Cell getExit() {
        return exit;
    }

    public Cell[][] getGrid() {
        return grid;
    }

    
    public int getWidth() {
        return width;
    }

    
    public int getHeight() {
        return height;
    }

    
    public int getCellSize() {
        return cellSize;
    }

    
    public int getElapsedTime() {
        return elapsedTime;
    }

    
     //Resets the timer (stops the game and sets time to 0).
     
    public void resetTimer() {
        stopGame();
        elapsedTime = 0;
    }

    
    //PRIVATE METHODS
     
    
     //Initializes the maze grid with empty cells.
     
    private Cell[][] initializeGrid() {
        Cell[][] newGrid = new Cell[height][width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                newGrid[y][x] = new Cell(x, y);
            }
        }
        return newGrid;
    }

    
     //Initializes the player at the entrance.
    
    private void initializePlayer() {
        player = new Player(entrance);
        drawPlayerPosition();
    }

    
     //Runs the main game loop for single-player mode.
     
    private void runGameLoop() {
        while (isGameRunning) {
            System.out.print("Move (WASD/solve/next/save/load/q): ");
            String input = scanner.nextLine().toLowerCase();
            if (input.isEmpty()) continue;

            switch (input) {
                case "q" -> {
                    stopGame();
                    return;
                }
                case "solve" -> solveMaze();
                case "next" -> provideNext10Steps();
                case "save" -> saveGame();
                case "load" -> loadGame();
                default -> {
                    if ("wasd".indexOf(input.charAt(0)) != -1) {
                        movePlayer(input.charAt(0));
                    }
                }
            }
        }
        stopGame();
    }

    
    //Runs the main loop for competitive mode.
     
    private void runCompetitiveGameLoop() {
        System.out.println("Competitive Mode! Take turns moving.");
        System.out.println("Use WASD keys to move, 'q' to quit.");

        while (isGameRunning) {
            if (isHumanTurn) {
                System.out.print("Your turn (WASD/save/load/q): ");
                String input = scanner.nextLine().toLowerCase();
                if (input.isEmpty()) continue;

                switch (input) {
                    case "q" -> {
                        stopGame();
                        return;
                    }
                    case "save" -> saveGame();
                    case "load" -> loadGame();
                    default -> {
                        if ("wasd".indexOf(input.charAt(0)) != -1) {
                            movePlayer(input.charAt(0));
                            isHumanTurn = false;
                            System.out.println("Moves - You: " + humanMoveCount + ", AI: " + aiMoveCount);
                        }
                    }
                }
            } else {
                // AI's turn
                System.out.println("AI's turn...");
                makeAIMove();
                aiMoveCount++;
                isHumanTurn = true;
                System.out.println("Moves - You: " + humanMoveCount + ", AI: " + aiMoveCount);
                delay(500);
            }
            redrawMazeAndPlayers();
        }
    }

    
      //Makes a single AI move from its planned path.
     
    private void makeAIMove() {
        if (!aiPlannedMoves.isEmpty()) {
            char move = aiPlannedMoves.remove(0);
            moveAIPlayer(move);
        }
    }

    
     //Moves the AI player in the given direction.
    
    private void moveAIPlayer(char direction) {
        if (aiPlayer == null) return;
        int[] movement = getMovementDeltas(direction);
        int dx = movement[0], dy = movement[1];

        if (canMove(aiPlayer.x, aiPlayer.y, dx, dy)) {
            updateAIPosition(dx, dy);
            checkAIWinCondition();
        }
    }

    
     //Updates the AI player's position.
    
    private void updateAIPosition(int dx, int dy) {
        aiPlayer.x += dx;
        aiPlayer.y += dy;
    }

    
     //checks if AI has reached the exit.
     
    private void checkAIWinCondition() {
        if (aiPlayer.x == exit.x && aiPlayer.y == exit.y) {
            int finalTime = elapsedTime;
            redrawMazeAndPlayers();
            stopGame();
            System.out.println("Game Over! AI wins in " + aiMoveCount + " moves!");
            System.out.println("Time elapsed: " + finalTime + " seconds");
            System.out.println("Your moves: " + humanMoveCount);
        }
    }

    
     //Redraws the entire maze plus both players (in competitive mode).
    
    private void redrawMazeAndPlayers() {
        if (turtle != null) {
            turtle.reset();
            draw();
            if (isCompetitiveMode) {
                drawBothPlayers();
            } else {
                drawPlayerPosition();
            }
        }
    }

    
      //Draws the human and AI players on the maze.
     
    private void drawBothPlayers() {
        
        turtle.color(PLAYER_COLOR);
        drawPlayer(player, cellSize / 3);

        
        turtle.color(AI_COLOR);
        drawPlayer(aiPlayer, cellSize / 3);
    }

    
     //Draws a single player (human or AI) as a circle in the maze.
     
    private void drawPlayer(Player p, double radius) {
        if (turtle == null || p == null) return;
        double centerX = p.x * cellSize + cellSize / 2.0;
        double centerY = p.y * cellSize + cellSize / 2.0;

        turtle.moveTo(centerX + radius, centerY);
        turtle.penDown();
        for (int i = 0; i <= 360; i += 10) {
            double radians = Math.toRadians(i);
            double x = centerX + radius * Math.cos(radians);
            double y = centerY + radius * Math.sin(radians);
            turtle.lineTo(x, y);
        }
        turtle.penUp();
    }

    //Timer Management

    
    //Starts the main game timer and sets isGameRunning = true.
    
    private void startTimer() {
        if (gameTimer != null) return;

        isGameRunning = true;

        gameTimer = new Timer();
        gameTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                elapsedTime++;
            }
        }, 1000, 1000);
    }

    
    //Starts the regeneration timer (auto-regenerates the maze every REGENERATION_TIME seconds).
     
    private void startRegenerationTimer() {
        startRegenerationTimer(REGENERATION_TIME);
    }
private void startRegenerationTimer(int initialDelaySeconds) {
    regenerationTimer = new Timer();
    regenerationTimer.scheduleAtFixedRate(
        new TimerTask() {
            @Override
            public void run() {
                regenerateMaze();
            }
        },
        initialDelaySeconds * 1000L,  
        REGENERATION_TIME * 1000L     
    );
}

    
    //Stops all timers and ends the game.
    
    private void stopGame() {
        isGameRunning = false;
        if (gameTimer != null) {
            gameTimer.cancel();
            gameTimer.purge();
            gameTimer = null;
        }
        if (displayTimer != null) {
            displayTimer.cancel();
            displayTimer.purge();
            displayTimer = null;
        }
        if (regenerationTimer != null) {
            regenerationTimer.cancel();
            regenerationTimer.purge();
            regenerationTimer = null;
        }
        aiSolution.clear();
        aiPlannedMoves.clear();
        
    }

    
     //Regenerates the maze while preserving player positions
     
    private synchronized void regenerateMaze() {
        if (isRegenerating || !isGameRunning) return;
        isRegenerating = true;

        // Store current positions
        Point currentPlayerPos = new Point(player.x, player.y);
        Point currentAIPos = (isCompetitiveMode && aiPlayer != null)
                             ? new Point(aiPlayer.x, aiPlayer.y)
                             : null;

        // Store old walls
        boolean[][][] oldWalls = new boolean[height][width][4];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                oldWalls[y][x] = Arrays.copyOf(grid[y][x].walls, 4);
            }
        }

        // Generate new maze
        generate();

        // Ensure path exists for both players
        if (!ensurePathExists(currentPlayerPos)
                || (isCompetitiveMode && !ensurePathExists(currentAIPos))) {
            // If no valid path, restore old maze
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    System.arraycopy(oldWalls[y][x], 0, grid[y][x].walls, 0, 4);
                }
            }
            generate(); 
        }
        
        // Recalculate AI solution if competitive
        if (isCompetitiveMode && currentAIPos != null) {
            aiSolution.clear();
            aiPlannedMoves.clear();
            findSolution(grid[currentAIPos.y][currentAIPos.x], new HashSet<>(), new ArrayList<>());
            aiPlannedMoves = new ArrayList<>(aiSolution);
        }

        // Update player positions
        player.x = currentPlayerPos.x;
        player.y = currentPlayerPos.y;
        if (isCompetitiveMode && aiPlayer != null && currentAIPos != null) {
            aiPlayer.x = currentAIPos.x;
            aiPlayer.y = currentAIPos.y;
        }

        // Redraw
        redrawMazeAndPlayers();
        isRegenerating = false;
        System.out.println("Maze regenerated! Keep going!");
    }

    
    //Ensures a path exists from the given start point to the exit.
     
    private boolean ensurePathExists(Point start) {
        if (start == null) return true;

        Set<Cell> visited = new HashSet<>();
        Stack<Cell> stack = new Stack<>();
        Cell startCell = grid[start.y][start.x];

        stack.push(startCell);
        visited.add(startCell);

        while (!stack.isEmpty()) {
            Cell current = stack.pop();
            if (current == exit) return true;

            for (int i = 0; i < 4; i++) {
                if (!current.walls[i]) {
                    int[] delta = getDeltaFromDirection(i);
                    int newX = current.x + delta[0];
                    int newY = current.y + delta[1];

                    if (isValidCell(newX, newY)) {
                        Cell next = grid[newY][newX];
                        if (!visited.contains(next)) {
                            visited.add(next);
                            stack.push(next);
                        }
                    }
                }
            }
        }
        return false;
    }

    //AI Solver Methods

    
     //Solves the maze (full AI solution from player's current position).
    private void solveMaze() {
        aiSolution.clear();
        Cell currentPos = getCell(player.x, player.y);
        findSolution(currentPos, new HashSet<>(), new ArrayList<>());

        if (aiSolution.isEmpty()) {
            System.out.println("No solution found!");
            return;
        }
        System.out.println("Showing full path in green...");

        // Draw the path in green, from the player's current position
        drawPathInGreen(currentPos, aiSolution);
    }

    
     //Finds a solution path from 'current' to the exit (using DFS ).
     
    private boolean findSolution(Cell current, Set<Cell> visited, List<Character> currentPath) {
        if (current == exit) {
            aiSolution.addAll(currentPath);
            return true;
        }
        visited.add(current);

        for (int i = 0; i < 4; i++) {
            if (!current.walls[i]) {
                int[] delta = getDeltaFromDirection(i);
                int newX = current.x + delta[0];
                int newY = current.y + delta[1];

                if (isValidCell(newX, newY) && !visited.contains(grid[newY][newX])) {
                    currentPath.add(getCharFromDirection(i));
                    if (findSolution(grid[newY][newX], visited, currentPath)) {
                        return true;
                    }
                    currentPath.remove(currentPath.size() - 1);
                }
            }
        }
        visited.remove(current);
        return false;
    }

    
    //Shows the next 10 moves of the AI solution from the player's position.
    
    private void provideNext10Steps() {
        aiSolution.clear();
        Cell currentPos = getCell(player.x, player.y);
        findSolution(currentPos, new HashSet<>(), new ArrayList<>());

        if (aiSolution.isEmpty()) {
            System.out.println("No solution available.");
            return;
        }
        int stepsToProvide = Math.min(10, aiSolution.size());
        List<Character> nextSteps = new ArrayList<>(aiSolution.subList(0, stepsToProvide));

        System.out.println("Showing next " + stepsToProvide + " steps in green...");
        drawPathInGreen(currentPos, nextSteps);
    }

    
    //delay for visualization.
    
    private void delay(int milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private int[] getDeltaFromDirection(int dir) {
        return switch (dir) {
            case 0 -> new int[]{0, -1};  // Up
            case 1 -> new int[]{1, 0};   // Right
            case 2 -> new int[]{0, 1};   // Down
            case 3 -> new int[]{-1, 0};  // Left
            default -> new int[]{0, 0};
        };
    }

   
    private char getCharFromDirection(int dir) {
        return switch (dir) {
            case 0 -> 'w';
            case 1 -> 'd';
            case 2 -> 's';
            case 3 -> 'a';
            default -> ' ';
        };
    }

    //Maze Generation mothods

    
     //Resets the entire grid, marking all cells unvisited and walls intact.
    
    private void resetGrid() {
        for (Cell[] row : grid) {
            for (Cell cell : row) {
                cell.visited = false;
                Arrays.fill(cell.walls, true);
            }
        }
    }

    
     //Finds an unvisited neighbor of the given cell.
    
    private Cell getUnvisitedNeighbor(Cell cell) {
        List<Cell> neighbors = new ArrayList<>();
        int[][] directions = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
        
        for (int[] dir : directions) {
            int newX = cell.x + dir[0];
            int newY = cell.y + dir[1];
            if (isValidCell(newX, newY) && !grid[newY][newX].visited) {
                neighbors.add(grid[newY][newX]);
            }
        }
        return neighbors.isEmpty() ? null : neighbors.get(random.nextInt(neighbors.size()));
    }

    
    //Removes walls between two adjacent cells.
    
    private void removeWalls(Cell current, Cell next) {
        int dx = next.x - current.x;
        int dy = next.y - current.y;

        if (dx == 1) { // right
            current.walls[1] = false;
            next.walls[3] = false;
        } else if (dx == -1) { // left
            current.walls[3] = false;
            next.walls[1] = false;
        } else if (dy == 1) { // down
            current.walls[2] = false;
            next.walls[0] = false;
        } else if (dy == -1) { // up
            current.walls[0] = false;
            next.walls[2] = false;
        }
    }

    
    //Creates an entrance and an exit on random sides of the maze.
     
    private void createEntranceAndExit() {
        int entranceSide = random.nextInt(4);
        int exitSide;
        do {
            exitSide = random.nextInt(4);
        } while (exitSide == entranceSide);

        entrance = createOpeningOnSide(entranceSide);
        exit = createOpeningOnSide(exitSide);
    }

    
    //Creates an opening on one of the four outer edges of the maze.
     
    private Cell createOpeningOnSide(int side) {
        Cell cell;
        switch (side) {
            case 0 -> { // Top
                cell = grid[0][random.nextInt(width)];
                cell.walls[0] = false;
            }
            case 1 -> { // Right
                cell = grid[random.nextInt(height)][width - 1];
                cell.walls[1] = false;
            }
            case 2 -> { // Bottom
                cell = grid[height - 1][random.nextInt(width)];
                cell.walls[2] = false;
            }
            case 3 -> { // Left
                cell = grid[random.nextInt(height)][0];
                cell.walls[3] = false;
            }
            default -> throw new IllegalArgumentException("Invalid side");
        }
        return cell;
    }

    //Movement Handling

    //Moves the player in the specified WASD direction.
    
    private void movePlayer(char direction) {
        if (player == null) return;
        int[] movement = getMovementDeltas(direction);
        int dx = movement[0], dy = movement[1];

        if (canMove(player.x, player.y, dx, dy)) {
            updatePlayerPosition(dx, dy);
            checkWinCondition();
            redrawMazeAndPlayer();
            humanMoveCount++;
        }
    }

    private int[] getMovementDeltas(char direction) {
        return switch (direction) {
            case 'w' -> new int[]{0, -1};   // Up
            case 's' -> new int[]{0, 1};    // Down
            case 'a' -> new int[]{-1, 0};   // Left
            case 'd' -> new int[]{1, 0};    // Right
            default -> new int[]{0, 0};
        };
    }

  
     //Checks if the player can move from (currentX, currentY) by (dx, dy).
    
    private boolean canMove(int currentX, int currentY, int dx, int dy) {
        if (!isValidCell(currentX + dx, currentY + dy)) {
            return false;
        }
        Cell currentCell = grid[currentY][currentX];
        return switch (getDirectionIndex(dx, dy)) {
            case 0 -> !currentCell.walls[0]; // Top
            case 1 -> !currentCell.walls[1]; // Right
            case 2 -> !currentCell.walls[2]; // Bottom
            case 3 -> !currentCell.walls[3]; // Left
            default -> false;
        };
    }

    private int getDirectionIndex(int dx, int dy) {
        if (dy < 0) return 0;   // Top
        if (dx > 0) return 1;   // Right
        if (dy > 0) return 2;   // Bottom
        if (dx < 0) return 3;   // Left
        return -1;
    }

    
    private void updatePlayerPosition(int dx, int dy) {
        player.x += dx;
        player.y += dy;
    }

    
    //Checks if the human player has reached the exit.

    private void checkWinCondition() {
        if (isCompetitiveMode) {
            if (player.x == exit.x && player.y == exit.y) {
                int finalTime = elapsedTime;
                redrawMazeAndPlayers();
                stopGame();
                System.out.println("Game Over! You win in " + humanMoveCount + " moves!");
                System.out.println("Time elapsed: " + finalTime + " seconds");
                System.out.println("AI moves: " + aiMoveCount);
            }
        } else {
            if (player.x == exit.x && player.y == exit.y) {
                int finalTime = elapsedTime;
                redrawMazeAndPlayer();
                stopGame();
                System.out.println("Congratulations! You completed the maze in " + finalTime + " seconds.");
            }
        }
    }

    //Drawing Methods

    
    //Redraws the maze and the single player (non-competitive mode).
     
    private void redrawMazeAndPlayer() {
        draw();
        drawPlayerPosition();
    }

    
     //Sets up the turtle for maze drawing (pen color, line width, etc.).
     
    private void setupTurtle() {
        turtle.reset();
        turtle.penDown();
        turtle.color(WALL_COLOR);
        turtle.lineWidth(2);
    }

    
    //Draws the maze structure (outer border and internal walls).
   
    private void drawMazeStructure() {
        drawOuterBorder();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                drawCellWalls(grid[y][x]);
            }
        }
    }

    
    //Draws the outer border of the maze.
    
    private void drawOuterBorder() {
        turtle.moveTo(0, 0);
        turtle.lineTo(width * cellSize, 0);
        turtle.lineTo(width * cellSize, height * cellSize);
        turtle.lineTo(0, height * cellSize);
        turtle.lineTo(0, 0);
    }

    
    //Draws walls for a single cell, if they exist.
     
    private void drawCellWalls(Cell cell) {
        double startX = cell.x * cellSize;
        double startY = cell.y * cellSize;

        // top wall
        if (cell.walls[0] && cell != entrance) {
            drawWall(startX, startY, startX + cellSize, startY);
        }
        // right wall
        if (cell.walls[1] && cell != exit) {
            drawWall(startX + cellSize, startY, startX + cellSize, startY + cellSize);
        }
        // bottom wall
        if (cell.walls[2] && cell != entrance && cell != exit) {
            drawWall(startX, startY + cellSize, startX + cellSize, startY + cellSize);
        }
        // left wall
        if (cell.walls[3] && cell != exit) {
            drawWall(startX, startY, startX, startY + cellSize);
        }
    }

    
    //Draws a single wall from (x1, y1) to (x2, y2).
     
    private void drawWall(double x1, double y1, double x2, double y2) {
        turtle.moveTo(x1, y1);
        turtle.lineTo(x2, y2);
    }

    
    //Draws the player's current position as a circle.
    
    private void drawPlayerPosition() {
        if (turtle == null || player == null) return;

        double centerX = player.x * cellSize + cellSize / 2.0;
        double centerY = player.y * cellSize + cellSize / 2.0;
        double radius = Math.min(cellSize, 20) / 3.0;

        turtle.color(PLAYER_COLOR);
        turtle.moveTo(centerX + radius, centerY);
        turtle.penDown();
        for (int i = 0; i <= 360; i += 10) {
            double radians = Math.toRadians(i);
            double x = centerX + radius * Math.cos(radians);
            double y = centerY + radius * Math.sin(radians);
            turtle.lineTo(x, y);
        }
        turtle.penUp();
    }

    //Colors the entrance and exit openings.
     
    private void colorEntranceAndExit() {
        turtle.color(ENTRANCE_COLOR);
        drawCellOpening(entrance);

        turtle.color(EXIT_COLOR);
        drawCellOpening(exit);
    }

    
    //Draws an opening on one side of the specified cell if there's no wall.
     
    private void drawCellOpening(Cell cell) {
        double startX = cell.x * cellSize;
        double startY = cell.y * cellSize;

        if (!cell.walls[0]) { // top open
            drawWall(startX, startY, startX + cellSize, startY);
        } else if (!cell.walls[1]) { // right open
            drawWall(startX + cellSize, startY, startX + cellSize, startY + cellSize);
        } else if (!cell.walls[2]) { // bottom open
            drawWall(startX, startY + cellSize, startX + cellSize, startY + cellSize);
        } else if (!cell.walls[3]) { // left open
            drawWall(startX, startY, startX, startY + cellSize);
        }
    }

    
    //Draws the timer (and move counter)
     
    private void drawTimer() {
        if (turtle == null) return;

        // Timer box 
        turtle.moveTo(30, 630);
        turtle.left(90);
        turtle.penUp();
        turtle.color(TIMER_TEXT_COLOR);
        turtle.penDown();
        turtle.forward(30).right(90).forward(80).right(90).forward(30).right(90).forward(80);
        turtle.right(90);
        turtle.penUp();

        // Timer text
        turtle.moveTo(50, 620);
        String timeString = formatTime(elapsedTime);
        turtle.text(timeString, Font.TIMES, 18, Font.Align.LEFT);

        // Move counter box
        turtle.moveTo(160, 630);
        turtle.penUp();
        turtle.color(TIMER_TEXT_COLOR);
        turtle.penDown();
        turtle.forward(30).right(90).forward(110).right(90).forward(30).right(90).forward(110);
        turtle.right(90);
        turtle.penUp();

        // Move counter text
        turtle.moveTo(165, 620);
        String movesString = isCompetitiveMode
                ? "Moves: " + humanMoveCount + "/" + aiMoveCount
                : "Moves: " + humanMoveCount;
        turtle.text(movesString, Font.TIMES, 18, Font.Align.LEFT);
    }

    
    //Formats the given number of seconds as MM:SS.
   
    private String formatTime(int seconds) {
        int minutes = seconds / 60;
        int remainingSeconds = seconds % 60;
        return String.format("%02d:%02d", minutes, remainingSeconds);
    }

    
    //Checks if (x, y) is within the bounds of the maze grid.
     
    private boolean isValidCell(int x, int y) {
        return x >= 0 && x < width && y >= 0 && y < height;
    }

    private Cell getCell(int x, int y) {
        return grid[y][x];
    }

    
    //Draws a given path in green from the given start cell.
    
    private void drawPathInGreen(Cell startCell, List<Character> moves) {
        int sx = startCell.x;
        int sy = startCell.y;

        
        turtle.reset();
        draw();  


       
        drawPlayerPosition();
        

       
        turtle.color(PATH_COLOR);
        turtle.penDown();

        double xPos = sx * cellSize + cellSize / 2.0;
        double yPos = sy * cellSize + cellSize / 2.0;
        turtle.moveTo(xPos, yPos);

        for (char move : moves) {
            int[] delta = getMovementDeltas(move);
            sx += delta[0];
            sy += delta[1];

            double newX = sx * cellSize + cellSize / 2.0;
            double newY = sy * cellSize + cellSize / 2.0;
            turtle.lineTo(newX, newY);
        }
        turtle.penUp();
    }
}
