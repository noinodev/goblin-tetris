import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.util.Map;

class Game extends scene { // main gameplay scene, i put it in its own class file because its huge. i couldve separated it into other classes but i dont really care

    // this has become even more of a giga class. my apologies! i dont have the manpower to refactor everything or write clean code.

    // i shouldve joined a team tbh, maybe next time

    // game world/state values
    public final int TET_WIDTH = 4;
    public int boardWidth,boardHeight,posx,posy,boardx,boardy,time,score,state,oldstate,clearx,cleary,clearflash,level,lines,nextTetronimo,lives; // yuck
    public double cleardy,cleardx,illum;
    public final Color[] flash = {new Color(255,255,255), new Color(255,0,68), new Color(99,199,77), new Color(44,232,245), new Color(254,231,97)}; // merge particle colours
    public final int scores[] = {40,100,300,1200}; // tetris scores
    public int[][] board;
    public int board_bound_x, board_bound_w;
    public double[][] light;
    public ObjectResource[] parallaxobj;
    public final BufferedImage levelimage = Tetris2805.loadTexture("resources/load/levelatlas.png");
    public GameObject playerObject;
    public ObjectTetromino currentTetromino;

    // took me way too long to write these consts, i was just remembering the integers before lol (notice how i missed 5 ????)
    public final int STATE_PLAY = 0, STATE_PAUSE = 1, STATE_GAMEOVER = 2, STATE_CLEAR = 3, STATE_LOSE = 4, STATE_oops = 5, STATE_STARTLEVEL = 6, STATE_ENDLEVEL = 7;

    // rendering surfaces for water and darkness
    private final BufferedImage[] water = {new BufferedImage(main.FRAMEBUFFER_W,main.FRAMEBUFFER_H,BufferedImage.TYPE_INT_ARGB),new BufferedImage(main.FRAMEBUFFER_W,main.FRAMEBUFFER_H,BufferedImage.TYPE_INT_ARGB)};
    private final Graphics2D[] water2d = {water[0].createGraphics(),water[1].createGraphics()}; // stupid double buffering
    private int watercontext;
    private final BufferedImage dark = new BufferedImage(main.FRAMEBUFFER_W,main.FRAMEBUFFER_H,BufferedImage.TYPE_INT_ARGB);
    private final Graphics2D dark2d = dark.createGraphics();

    // goblin specific vars, like for breaking and placing tiles
    public int wood,stone,tool;
    private int tbx,tby;
    private double tilebreak;
    public int game_start_wait;
    public int enemy_visible;
    public int chat;

    public double globallight;

    private void loadLevel(int w, int h){ // loads goblin levels from an image, colour data represents what goes where
        BufferedImage in = levelimage;
        for(int x = -w; x < 2*w; x++){
            for(int y = 0; y < h; y++){
                if(x >= 0 && x < in.getWidth()){
                    int[][] b = board;
                    int c = in.getRGB(x,y) & 0xFFFFFF;
                    b[x][y] = 0;
                    if(c == 0x0000FF) b[x][y] = 114; // bricks                    all sprites arent named and i couldnt be bothered
                    else if(c == 0x00FF00) b[x][y] = 160; // torch up             you kinda just gotta figure it out
                    else if(c == 0x00FF80) b[x][y] = 161; // torch left
                    else if(c == 0x80FF00) b[x][y] = 162; // torch right
                    else if(c == 0xFFFF00) b[x][y] = 190; // scaffold vertical
                    else if(c == 0xFFFF80) b[x][y] = 191; // scaffold horizontal
                    else if(c == 0xFF00FF) b[x][y] = 192; // pole
                    else if(c == 0xFF80FF) b[x][y] = 163; // flag
                    if(c == 0xFF0000 && b == board){
                        int spr = 137+10*(int)(Math.random()*3);
                        if(Math.random() < 0.05) spr = 187; // random chance of giga gooner spawn
                        GameObject.syncObject(new ObjectCharacter(this,PlayerControlScheme.PCS_AI,spr,posx+x*main.SPR_WIDTH,posy+y*main.SPR_WIDTH)); // goblin only spawn in board area
                    }
                }
            }
        }
    }

    // used to get the nearest valid light cell for a given position. this is how out-of-grid objects like entities, the background, and the walls, are given lighting
    public Color getLightLocal(double x, double y, double shift){
        int bx = Math.max(Math.min((int)(Math.floor(x-boardx)/main.SPR_WIDTH),boardWidth-1),0);
        int by = Math.max(Math.min((int)(Math.floor(y-boardy)/main.SPR_WIDTH),boardHeight-1),0);

        double disttocenter = Math.abs(x - (draw.view_x+draw.view_w/2))/(draw.view_w*0.5);
        double l = Math.min(1,Math.max(0, 1-(light[bx][by])/10f+shift + disttocenter*globallight));

        return new Color((int)D2D.lerp(255,24,l),(int)D2D.lerp(255,20,l),(int)D2D.lerp(255,37,l));
    }

    // get light level within the grid
    private int getLightLevel(int x,int y,double scale){
        // starting light distance is distance to current tetromino, as it makes the lighting smoother as it drops between cells
        double minDistance = 100;
        if(playerObject != null) minDistance = Math.sqrt(Math.pow((playerObject.x/main.SPR_WIDTH)+1 - x, 2) + Math.pow((playerObject.y/main.SPR_WIDTH)+1 - y, 2));
        for (int i = board_bound_x; i < board_bound_x+board_bound_w; i++) {
            for (int j = 0; j < boardHeight; j++) {
                if (board[i][j] != 0 && ((board[i][j] < 100 && Math.random() < 0.05) || (board[i][j] >= 160 && board[i][j] <= 162))) { // brick and scaffold tiles specifically are ignored kinda hacky until i add more decorations and fix it
                    double distance = Math.sqrt(Math.pow(i - x, 2) + Math.pow(j - y, 2)); // euclidean distance to target cell
                    minDistance = Math.min(minDistance, distance); // self explanatory
                }
            }
        }
        return (int)(10-Math.min(Math.max(minDistance*scale, 1),10)); // 10 light levels
    }

    private int getTileIndex(int i, int x, int y, int[][] tboard){ // autotiling helper function, just for bricks. its actually really slow !
        int l=0,r=0,t=0,b=0;
        if(x > 0 && tboard[x-1][y] == i) l = 1;
        if(x < tboard.length-1 && tboard[x+1][y] == i) r = 1;
        if(y > 0 && tboard[x][y-1] == i) t = 1;
        if(y < tboard[0].length-1 && tboard[x][y+1] == i) b = 1;
        int a = l+r+t+b;

        if(a == 4) return i;
        else if(a == 3){
            if(l == 0) return i-1;
            if(r == 0) return i+1;
            if(t == 0) return i-10;
            if(b == 0) return i+10;
        }else if(a == 2){
            if(t == 0 && l == 0) return i-11;
            if(t == 0 && r == 0) return i-9;
            if(b == 0 && l == 0) return i+9;
            if(b == 0 && r == 0) return i+11;
            if(t == 0 && b == 0) return i+2;
            if(l == 0 && r == 0) return i-8;
        }else if(a == 1){
            if(b == 1) return i+12;
            if(l == 1) return i-7;
            if(t == 1) return i+3;
            if(r == 1) return i+13;
        }
        return i-6;
    }

    public int pointCheck(double x, double y){ // collision function for enemies, check if out of bounds -> check if inside an occupied cell
        int bx = (int)Math.floor((x-posx)/main.SPR_WIDTH), by = (int)((y-posy)/main.SPR_WIDTH);
        if(bx < 0 || bx >= boardWidth || by < 0 || by >= boardHeight || (board[bx][by] > 0 && board[bx][by] < 160)) return 1;
        return 0;
    }

    // 2 functions that pretty much do the same thing
    // kinda hacky sorry

    public int clearRows(){
        int rows = 0;
        for(int i = boardHeight-1; i > 0; i--){
            int clear = 1;
            for(int j = board_bound_x; j < board_bound_x+board_bound_w; j++){
                if(board[j][i] == 0 || board[j][i] >= 160) clear = 0; // decorative tiles are all indexed above 160
            }
            if(clear == 1){
                for(int y = i; y > 1; y--){ // shift rows above row to be cleared
                    for(int x = board_bound_x; x < board_bound_x+board_bound_w; x++) board[x][y] = board[x][y-1];
                }
                rows++;
                break;
            }
        }
        return rows;
    }

    public int checkRows(){
        int rows = 0;
        for(int i = boardHeight-1; i > 0; i--){
            int clear = 1;
            for(int j = board_bound_x; j < board_bound_x+board_bound_w; j++){
                if(board[j][i] == 0 || board[j][i] >= 160) clear = 0; // decorative tiles are all indexed above 160
            }
            if(clear == 1){
                if(rows == 0){ // reset row clear animation for first found row, i would break here but this function also counts how many rows are cleared at once for scoring
                    cleary = i;
                    cleardy = 0;
                    clearflash = (int)(Math.random()*5);
                }
                rows++; // count the number of rows for scoring purposes

                // refactoring particles was one of my biggest laments of implementing the new entity system
                for(int k = 0; k < 10; k++){
                    GameObject.CreateObject(new ObjectParticle(this,
                       boardx+(int)(board_bound_x+(board_bound_w/2.)*main.SPR_WIDTH*Math.random()),
                       boardy+cleary*main.SPR_WIDTH, -0.1+0.2*Math.random(),-0.1+0.2*Math.random(),
                       29,34,0.05+0.05*Math.random(), 240, flash[(int)(Math.random()*5)])
                    );
                }
                // particle effect ^^
            }
        }
        return rows;
    }

    // loop function for game-specific networking
    public void networkUpdate(){
        switch(main.gamemode){
            case GM.GM_HOST: {
                // send object states every 8 frames, if an object isnt currently being added by NetworkManager
                // poor mans mutex but it works
                if((int)main.frame%8 == 0 && GameObject.lock == 0){
                    if(GameObject.netobjects.size() > 0){
                        // serial buffer for basic object information
                        ByteBuffer buffer = NetworkManager.packet_start(NPH.NET_OBJ);
                        int nc = 0;
                        for (Map.Entry<String, GameObject> le : GameObject.netobjects.entrySet()) {
                            GameObject obj = le.getValue();
                            if(obj != null && obj.destroy == 0 && obj.change == 1) nc++;
                        }

                        buffer.putInt(nc);

                        for (Map.Entry<String, GameObject> le : GameObject.netobjects.entrySet()) {
                            GameObject obj = le.getValue();
                            if(obj != null && obj.destroy == 0 && obj.change == 1){
                                //buffer = NetworkHandler.packet_start(NPH.NET_OBJ,obj.UID);
                                buffer.put(obj.UID.getBytes());
                                buffer.put(obj.inst);
                                buffer.putDouble(obj.x);
                                buffer.putDouble(obj.y);
                                buffer.putDouble(obj.sprite);
                                buffer.putDouble(obj.hsp);
                                buffer.putDouble(obj.vsp);
                                obj.change = 0;
                            }
                        }
                        NetworkManager.send_all(buffer);
                    }
                }

                // send board state to everybody every 16 frames
                if((int)main.frame%16 == 0){
                    ByteBuffer buffer = NetworkManager.packet_start(NPH.NET_STATE);
                    ObjectTetromino t = (ObjectTetromino)playerObject;
                    buffer.putInt(t.dx);
                    buffer.putInt(t.dy);
                    buffer.putInt(t.index);
                    buffer.putInt(t.rotation);
                    buffer.putDouble(t.x);
                    buffer.putDouble(t.y);
                    buffer.putDouble(t.sprite);

                    buffer.put((byte)board_bound_x);
                    buffer.put((byte)board_bound_w);
                    buffer.put((byte)boardHeight);
                    for (int i = board_bound_x; i < board_bound_x+board_bound_w; i++) {
                        for (int j = 0; j < boardHeight; j++){
                            buffer.putInt(board[i][j]);
                        }
                    }
                    NetworkManager.send_all(buffer);
                }

            } break;
            case GM.GM_JOIN: {
                state = STATE_PLAY; // permanent play-state
                // load board state
                if(NetworkManager.async_load.get("game.state.board") != null){
                    // use async_load to fetch data from NetworkManager semi-safely
                    int x = (int) NetworkManager.async_load.get("game.state.pos");
                    int w = (int) NetworkManager.async_load.get("game.state.width");
                    int h = (int) NetworkManager.async_load.get("game.state.height");

                    board_bound_x = x;
                    board_bound_w = w;
                    for (int i = 0; i < w; i++) {
                        for (int j = 0; j < h; j++) {
                            if(i+x >= 0 && i+x < boardWidth) board[i+x][j] = ((int[][]) NetworkManager.async_load.get("game.state.board"))[i][j]; //yucky!
                        }
                    }

                    NetworkManager.async_load.remove("game.state.board");
                }

                // load tetromino state
                if(NetworkManager.async_load.get("game.state.dx") != null){
                    currentTetromino.dx = (int) NetworkManager.async_load.get("game.state.dx");
                    currentTetromino.dy = (int) NetworkManager.async_load.get("game.state.dy");
                    currentTetromino.index = (int) NetworkManager.async_load.get("game.state.index");
                    currentTetromino.rotation = (int) NetworkManager.async_load.get("game.state.rot");
                    currentTetromino.x -= (currentTetromino.x-(double) NetworkManager.async_load.get("game.state.x"))*0.2;
                    currentTetromino.y -= (currentTetromino.y-(double) NetworkManager.async_load.get("game.state.y"))*0.2;
                    currentTetromino.sprite = (double) NetworkManager.async_load.get("game.state.sprite");
                    NetworkManager.async_load.remove("game.state.dx");
                }

                // send playerobject information to host
                if((int)main.frame%8 == 0){
                    if(playerObject != null){
                        ByteBuffer buffer = NetworkManager.packet_start(NPH.NET_OBJ);
                        buffer.putInt(1);
                        buffer.put(main.UID.getBytes());
                        buffer.put(playerObject.inst);
                        buffer.putDouble(playerObject.x);
                        buffer.putDouble(playerObject.y);
                        buffer.putDouble(playerObject.sprite);
                        buffer.putDouble(playerObject.hsp);
                        buffer.putDouble(playerObject.vsp);
                        NetworkManager.send_all(buffer);
                    }
                }
                // send username to the host every now and then
                if((int)main.frame%main.TPS == 0){
                    if(playerObject != null){
                        ByteBuffer buffer = NetworkManager.packet_start(NPH.NET_CHAT);
                        buffer.put((byte)1);
                        buffer.put((byte)1);
                        buffer.put(main.UID.getBytes());
                        buffer.put((byte)((String)main.cfg.get("username")).length());
                        buffer.put(((String)main.cfg.get("username")).getBytes());
                        NetworkManager.send_all(buffer);
                    }
                }
            } break;
        }
    }

    public Game(Tetris2805 m, D2D d) { // this is really gross
        // here begins the giga class
        super(m, d);
        D2D.clearColour = new Color(24,20,37);
        main.sceneIndex = 4;
        GameObject.g = this;
        GameObject.DestroyAllObjects(); // ensure there are no active objects from previous sessions, as the object list is static
        NetworkManager.game = this;

        globallight = 0;
        game_start_wait = 0;
        watercontext = 0;

        posx = 80; // board anchor position
        posy = 6;
        boardx = posx;
        boardy = posy;

        tbx = 0;
        tby = 0;
        tilebreak = 0;
        time = 0;
        score = 0;
        state = 0;
        oldstate = state;
        lines = 0;
        illum = 1;
        clearx = 0;
        cleary = 0;
        cleardy = 0;
        cleardx = 0;
        wood=20;
        stone=10;
        enemy_visible = 0;
        chat = 0;

        AudioManager.audio.get("ambienthigh").play(0,0,0,0);
        AudioManager.audio.get("ambientlow").play(0,0,0,0);

        // create a tetromino that may or may not be the player
        currentTetromino = (ObjectTetromino)GameObject.CreateObject(new ObjectTetromino(this,PlayerControlScheme.PCS_EXTERN,0,0,0,0,0));

        switch(main.gamemode){
            case GM.GM_OFFLINE:{
                // goblin mode, or otherwise offline
                boardWidth = levelimage.getWidth();
                boardHeight = (Integer)main.cfg.get("height")+2;
                board_bound_x = 0;
                board_bound_w = (Integer)main.cfg.get("width");

                nextTetronimo = (int)(Math.random() * ObjectTetromino.tetrominoList.length); // random tetromino index
                playerObject = currentTetromino;

                ObjectTetromino t = (ObjectTetromino) playerObject; // reset playerobject
                t.ResetTetromino();
                t.control_scheme = PlayerControlScheme.PCS_LOCAL;

                level = (Integer)main.cfg.get("level"); // starting level
                lives = 1+2*(Integer)main.cfg.get("extend"); // only goblin mode has 3 lives
                board = new int[boardWidth][boardHeight]; // board init
                light = new double[boardWidth][boardHeight]; // light init
                for(int i = 0; i < boardWidth; i++){
                    for(int j = 0; j < boardHeight; j++){
                        board[i][j] = 0;
                        light[i][j] = 0;
                    }
                }
                if((Integer)main.cfg.get("extend") == 1) loadLevel(boardWidth,boardHeight); // first goblin level
            }break;
            case GM.GM_HOST:{
                game_start_wait = (int)(30*main.TPS);
                boardWidth = 100;
                boardHeight = (Integer)main.cfg.get("height")+2;
                board_bound_x = 0;
                board_bound_w = (Integer)main.cfg.get("width");

                nextTetronimo = (int)(Math.random() * ObjectTetromino.tetrominoList.length); // random tetromino
                playerObject = currentTetromino;
                ObjectTetromino t = (ObjectTetromino) playerObject;
                t.ResetTetromino();
                t.control_scheme = PlayerControlScheme.PCS_LOCAL;
                level = (Integer)main.cfg.get("level"); // starting level
                lives = 3; // multiplayer is goblin mode only
                board = new int[boardWidth][boardHeight]; // board init
                light = new double[boardWidth][boardHeight]; // light init
                for(int i = 0; i < boardWidth; i++){
                    for(int j = 0; j < boardHeight; j++){
                        board[i][j] = 0;
                        light[i][j] = 0;
                    }
                }

                if((Integer)main.cfg.get("extend") == 1) loadLevel(boardWidth,boardHeight);

                // preload client player objects, even though its not necessary just makes it less likely for something to go wrong
                for (Map.Entry<String, Client> entry : NetworkManager.clients.entrySet()) {
                    Client i = entry.getValue();
                    if(i != null && i.UID != main.UID){
                        i.agent = GameObject.syncObject(new ObjectCharacter(this,PlayerControlScheme.PCS_EXTERN,137,boardx+40,boardy+10));
                    }
                }
            }break;
            case GM.GM_JOIN:{
                game_start_wait = (int)(30*main.TPS);
                boardWidth = 100;
                boardHeight = (Integer)main.cfg.get("height")+2;
                board_bound_x = 0;
                board_bound_w = (Integer)main.cfg.get("width");
                nextTetronimo = 0; // no tetrmonio here
                playerObject = GameObject.CreateObject(new ObjectCharacter(this,PlayerControlScheme.PCS_LOCAL,137,boardx+40,boardy+10)); // goblin player object
                if((Integer)main.cfg.get("ai") == 1) ((ObjectCharacter)playerObject).control_scheme = PlayerControlScheme.PCS_AI; // goblin ai

                level = (Integer)main.cfg.get("level"); // starting level
                lives = 3; // multiplayer is goblin mode only
                board = new int[boardWidth][boardHeight]; // board init
                light = new double[boardWidth][boardHeight]; // light init
                for(int i = 0; i < boardWidth; i++){
                    for(int j = 0; j < boardHeight; j++){
                        board[i][j] = 0;
                        light[i][j] = 0;
                    }
                }
            }break;
        }

        // ai control thread, only runs if ai is enabled and stops if the scene changes
        if((Integer)main.cfg.get("ai") == 1 && main.gamemode != GM.GM_JOIN){
            Thread aithread = new Thread(() -> {
                // 30 ticks per second
                double expectedFrametime = 1000000000 / (30.);
                Object[] b = null; // getBestPosition returns an array of Object (idiot tuple perchance)
                ObjectTetromino best = null;
                ObjectTetromino[] pieces = null;
                while(state != STATE_GAMEOVER && main.sceneIndex == 4){
                    long now = System.nanoTime();
                    // get two visible pieces in sequence: current and next
                    if(currentTetromino != null){
                        pieces = new ObjectTetromino[2];
                        pieces[0] = new ObjectTetromino(currentTetromino);
                        pieces[0].dx -= board_bound_x;
                        pieces[1] = new ObjectTetromino(currentTetromino);
                        pieces[1].dx = board_bound_w/2-TET_WIDTH/2;
                        pieces[1].dy = 0;
                        pieces[1].index = nextTetronimo;

                        // get best potential position for the current board/player state
                        Object[] pb = null;
                        if(pieces != null) pb = RobotManager.getBestPosition(board,board_bound_x,board_bound_w,pieces,0);
                        if(pb != null) b = pb;
                        if(b != null && b[0] != null){
                            ((ObjectTetromino)b[0]).dx += board_bound_x;
                            best = (ObjectTetromino)b[0];
                        }
                        // use input controls to move tetromino to desired position
                        if(best != null){
                            if(currentTetromino.dx < best.dx) main.input.put(KeyEvent.VK_RIGHT,1);
                            if(currentTetromino.dx > best.dx) main.input.put(KeyEvent.VK_LEFT,1);
                            if(currentTetromino.rotation != best.rotation) main.input.put(KeyEvent.VK_UP,1);
                            if(currentTetromino.dx == best.dx && currentTetromino.rotation == best.rotation) main.input.put(KeyEvent.VK_DOWN,1);
                            currentTetromino.dy++;
                            if(!currentTetromino.checkBoardState()) main.input.put(KeyEvent.VK_DOWN,1);
                            currentTetromino.dy--;
                        }else main.input.put(KeyEvent.VK_DOWN,1);//System.out.println("given up..");
                    }

                    long timeTaken = System.nanoTime() - now, sleepTime = (long)(expectedFrametime - timeTaken);
                    main.frametimes[2] = timeTaken; // ai thread profiling
                    if (sleepTime > 0) {
                        try {
                            Thread.sleep(sleepTime / 1000000, (int)(sleepTime % 1000000));
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }
            });
            aithread.start();
        }

        // create parallax background rocks/trees
        parallaxobj = new ObjectResource[boardWidth];
        for(int i = 0; i < boardWidth; i++){
            parallaxobj[i] = new ObjectResource(this,(int)(boardx+boardWidth*main.SPR_WIDTH*Math.random()),boardy+boardHeight*main.SPR_WIDTH,0.2+(0.3/boardWidth)*(boardWidth-i),Math.random() > 0.5 ? 109 : 119,10);
        }
    }

    @Override
    public void loop(){
        // animations and interpolations for various states

        // ambient sound/music player
        if((Integer)main.cfg.get("music") == 1){
            if(!AudioManager.audio.get("ambienthigh").playing()) AudioManager.audio.get("ambienthigh").play(0,0,0,0);
            if(!AudioManager.audio.get("ambientlow").playing()) AudioManager.audio.get("ambientlow").play(0,0,0,0);
            AudioManager.audio.get("ambientlow").pos(0, draw.view_y+draw.view_h/2,-80,boardy+boardHeight*main.SPR_WIDTH);
            AudioManager.audio.get("ambienthigh").pos(0, draw.view_y+draw.view_h/2,80,boardy+(boardHeight/4)*main.SPR_WIDTH);
        }

        enemy_visible = 0;
        boardx -= Math.ceil(boardx-posx)*0.2;
        boardy -= Math.ceil(boardy-posy)*0.2;
        illum -= (illum-(2+1*(time/main.TPS)))*0.05;

        // get tiles that are visible on board
        int boardvisw = (int)(main.FRAMEBUFFER_W/main.SPR_WIDTH)+1, boardvisx = Math.min(boardWidth-boardvisw,Math.max(0,(int)((draw.view_x-boardx)/main.SPR_WIDTH)));
        double bgw = 256;

        // draw parallax background
        double bgx = draw.view_x;
        double bgy = draw.view_y;
        int[] bgtex = {42,61,60,52,51,50};
        for(int i = 0; i < bgtex.length; i++){
            for(int j = 0; j <= (boardWidth*main.SPR_WIDTH)/bgw; j += 1){
                draw.batchPush(bgtex[i],(int)((bgx*(1.-0.1*i))%bgw+bgw*j),(int)((bgy*(1-0.1*i))%bgw),bgw,bgw);
            }
        }

        // calculate current light level, technically a day/night cycle
        double moonlight = 1-(level/10.);
        if(main.cfg.get("light") != null) moonlight = ((Integer)main.cfg.get("light")/10.)-(level/10.);
        globallight = moonlight-(game_start_wait/(30*main.TPS))*moonlight;
        if(game_start_wait > 0) game_start_wait--;

        // draw darkness layer
        if((int)main.frame%60==0){
            dark2d.setComposite(AlphaComposite.Src);
            int alpha = (int)(255 * globallight * 0.9);
			alpha = Math.max(0, Math.min(alpha, 255));

			dark2d.setColor(new Color(0, 0, 5, alpha));
            dark2d.fillRect(0, 0, dark.getWidth(), dark.getHeight());
        }

        draw.batchPush(dark,draw.view_x,draw.view_y,draw.view_w,draw.view_h);

        for(int i = 0; i < parallaxobj.length; i++)parallaxobj[i].update(); // update method for parallax objects

        networkUpdate(); // run network update as above

        // just needed better scoping for suffixes at the bottom of this giga class
        String control = "";
        if((Integer)main.cfg.get("ai") == 1) control = " [AI]";
        if(main.gamemode == GM.GM_JOIN) control = " [MP]";

        // main gameplay / render loop
        time++;
        if(state != STATE_GAMEOVER){
            if(main.input.get(KeyEvent.VK_ESCAPE) == 1 || main.input.get(KeyEvent.VK_P) == 1) state = 1-state; // pause input
            main.bgtx = score+800*level+main.frame*0.1;

            //play area borders
            draw.batchPush(9,boardx+board_bound_x*main.SPR_WIDTH,boardy+2*main.SPR_WIDTH,1,(boardHeight-2)*main.SPR_WIDTH+1,new Color(139,155,180));
            draw.batchPush(9,boardx+(board_bound_x+board_bound_w)*main.SPR_WIDTH,boardy+2*main.SPR_WIDTH,1,(boardHeight-2)*main.SPR_WIDTH+1,new Color(139,155,180));


            // calculate light levels for visible cells
            for(int i = boardvisx; i < boardvisx+boardvisw; i++){
                for(int j = 2; j < boardHeight; j++){
                    double tl = getLightLevel(i,j,illum)*4;
                    if(i < boardWidth-1) tl += light[i+1][j];
                    if(i > 0) tl += light[i-1][j];
                    if(j < boardHeight-1) tl += light[i][j+1];
                    if(j > 0) tl += light[i][j-1];
                    tl /= 8;
                    light[i][j] -= (light[i][j]-tl)*(0.05/illum); // this didnt need to look so hacky but i wanted the lights to be smoother and take the average of adjacent cells
                    if(board[i][j] < 0) board[i][j] = 0; // moving tetromino sets board cells to negative values, reset this
                }
            }

            int lo = 0;
            if(state == STATE_STARTLEVEL){ // level clear animation i think this is mostly unused now
                cleardx -= ((board_bound_w+1)*main.SPR_WIDTH-cleardx)*0.01; // cleardx is the horizontal scrolling of the board between levels
                boardx += (int)(cleardx*0.01)-(int)(Math.random()*(cleardx*0.02));
                boardy += (int)(cleardx*0.01)-(int)(Math.random()*(cleardx*0.02));
                illum = 10;
                if(cleardx <= 0.1){
                    state = STATE_PLAY;
                    cleardx = 0;
                }
            }

            //filled board space
            for(int i = boardvisx; i < boardvisx+boardvisw; i++){
                for(int j = 2; j < boardHeight; j++){
                    if(board[i][j] > 0 && j != cleary){
                        int k = board[i][j];
                        if(k == 114) k = getTileIndex(k,i,j,board); // the brick tile is the only tile with autotiling
                        else if(k >= 160 && k < 170) k += ((int)(main.frame/(main.TPS/6))%3)*10; // torch animation

                        Color c = getLightLocal(boardx+i*main.SPR_WIDTH,boardy+j*main.SPR_WIDTH,0);
                        draw.batchPush(k,boardx+i*main.SPR_WIDTH + lo,boardy+j*main.SPR_WIDTH + (j < cleary ? (int)cleardy : 0), main.SPR_WIDTH,main.SPR_WIDTH,c);
                        // ternary operator to only draw rows above cleardy in animated state for row clear, also lerp for light level
                    }
                }
            }

            // draw grassy ground
            for(int i = boardvisx; i <= boardvisx+boardvisw; i += 1){
                Color c = getLightLocal(boardx+i*main.SPR_WIDTH,boardy+boardHeight*main.SPR_WIDTH,0);
                draw.batchPush(100+Math.abs(i)%3,boardx+i*main.SPR_WIDTH,(i/main.SPR_WIDTH*i)%3-1+boardy+boardHeight*main.SPR_WIDTH, main.SPR_WIDTH, main.SPR_WIDTH,c);
                draw.batchPush(110+Math.abs(i)%3,boardx+i*main.SPR_WIDTH,(i/main.SPR_WIDTH*i)%3-1+boardy+(boardHeight+1)*main.SPR_WIDTH, main.SPR_WIDTH, main.SPR_WIDTH,c);
                draw.batchPush(120+Math.abs(i)%3,boardx+i*main.SPR_WIDTH,(i/main.SPR_WIDTH*i)%3-1+boardy+(boardHeight+2)*main.SPR_WIDTH, main.SPR_WIDTH, main.SPR_WIDTH,c);
            }

            // update all gameobjects, including netobjects
            if(GameObject.objects.size() > 0){
                for(int i = 0; i < GameObject.objects.size(); i++){
                    GameObject obj = GameObject.objects.get(i);
                    if(obj != null && obj.destroy == 0){
                        obj.update();
                    }else{
                        GameObject.objects.remove(i);
                        i--;
                    }
                }
            }

            // if the player is a goblin and in ai mode, the object itself doesnt control the camera so this hack makes that happen here
            if((Integer)main.cfg.get("ai") == 1 && main.gamemode == GM.GM_JOIN){
                draw.view_x -= (draw.view_x-(playerObject.x-main.FRAMEBUFFER_W/2.))*0.05;
                draw.view_y -= (draw.view_y-(playerObject.y-main.FRAMEBUFFER_H/2.))*0.05;
            }

            // draw world border slopes
            for(int i = 0; i < boardHeight+3; i++){
                Color cl = getLightLocal(boardx+1,boardy+i*main.SPR_WIDTH,0);
                Color cr = getLightLocal(boardx+boardWidth*main.SPR_WIDTH-1,boardy+i*main.SPR_WIDTH,0);
                draw.batchPush(184,boardx-200-(boardHeight-i)*3,boardy+i*main.SPR_WIDTH,200,10,cl);
                draw.batchPush(185, boardx+boardWidth*main.SPR_WIDTH+(boardHeight-i)*3, boardy+i*main.SPR_WIDTH,200,10,cr);
            }

            // draw double buffered water
            watercontext = 1-watercontext;
            BufferedImage _water = water[watercontext];
            Graphics2D _water2d = water2d[1-watercontext];
            int waterY = (int)(boardy+(boardHeight+3)*main.SPR_WIDTH-draw.view_y);
            int waterHeight = main.FRAMEBUFFER_H-waterY;

            // water reflection surface
            _water2d.setComposite(AlphaComposite.Clear);
            _water2d.fillRect(0, 0, water[1-watercontext].getWidth(), water[1-watercontext].getHeight());
            _water2d.setComposite(AlphaComposite.SrcOver);
            _water2d.setClip(0, waterY, water[1-watercontext].getWidth(), waterHeight);

            AffineTransform flip = new AffineTransform();
            flip.scale(1, -0.5);
            flip.translate(0, -3*waterY);

            _water2d.drawImage(draw.framebuffer[1-draw.gcontext], flip, null);
            draw.batchPush(_water,draw.view_x,draw.view_y,draw.view_w,draw.view_h);

            // water highlights
            for(int i = 1; i <= 12; i += 2){
                int s = 118, h = 1;
                for(int j = 0; j <= (boardWidth*main.SPR_WIDTH)/bgw; j += 1){
                    draw.batchPush(s,(int)(((boardWidth*main.SPR_WIDTH*0.5-bgx)*(1.+0.02*i)+(Math.sin(i*234)*0.06)*main.frame+25*i)%bgw+bgw*j),(int)(waterY+(draw.view_y)*(1+0.01*i))+2*i,bgw,h);
                }
            }

            // foreground rocks/bushes
            int[] fgtex = {129,128,135,136};
            for(int i = 0; i < fgtex.length; i++){
                for(int j = 0; j <= (boardWidth*main.SPR_WIDTH)/bgw; j += 1){
                    draw.batchPush(fgtex[i],(int)((-bgx*(0.4+0.5*i))%bgw+bgw*j-bgw),(int)(waterY+(draw.view_y)*(1.1+0.02*i))-10*i,bgw,128);
                }
            }

            // row clear when youre good. stops main gameplay loop and sequentially clears rows
            if(state == STATE_CLEAR){
                illum *= 0.9; // illuminate the board
                cleardy += main.SPR_WIDTH/(16*(1+cleardy)); // cleardy animation for clearing a row
                boardx += 2-(int)(Math.random()*4);
                boardy += 2-(int)(Math.random()*4); // shake the board
                double i = cleardy/main.SPR_WIDTH;
                // coloured rectangle that squishes as the board clears a row
                draw.batchPush(9,boardx,boardy+cleary*main.SPR_WIDTH+(int)cleardy,boardWidth*main.SPR_WIDTH,Math.max(1,1+main.SPR_WIDTH-(int)cleardy),
                        new Color((int)draw.lerp(flash[clearflash].getRed(),24,i),(int)draw.lerp(flash[clearflash].getBlue(),20,i),(int)draw.lerp(flash[clearflash].getGreen(),37,i)));
                if(cleardy > main.SPR_WIDTH){
                    // anim reset, check if more rows need to be cleared
                    for(int k = 0; k < 10; k++){
                        GameObject.CreateObject(new ObjectParticle(this,
                            boardx+(int)(boardWidth*main.SPR_WIDTH*Math.random()),
                            boardy+cleary*main.SPR_WIDTH, 0,0,
                            30,33,0.05+0.05*Math.random(), 240, Color.WHITE)
                        );
                    }
                    AudioManager.audio.get("tap2").play(0,0,0,0);
                    cleardy = 0;
                    cleary = 0;
                    clearRows();
                    lines++;
                    if(lines > 10*(level+1) && (Integer)main.cfg.get("extend") == 0) level++; // next level if cleared 10+ rows on current level
                    if(checkRows() == 0) state = STATE_PLAY;
                }
            }else cleary = 0; // reset animation

            // lose animation
            if((state == STATE_LOSE) && main.gamemode != GM.GM_JOIN){ // lose / level transition board clear animation
                int x = board_bound_x+clearx%board_bound_w, y = clearx/board_bound_w; // x y coords from animation state clearx
                if(x >= 0 && x < boardWidth && y >= 0 && y < boardHeight && board[x][y] > 0 && board[x][y] < 100){ // only clear tetromino sprites
                    board[x][y] = 0;
                    for(int k = 0; k < 3; k++){
                        GameObject.CreateObject(new ObjectParticle(this,
                                boardx+(int)(x*main.SPR_WIDTH+main.SPR_WIDTH*Math.random()),
                                boardy+y*main.SPR_WIDTH+(int)(main.SPR_WIDTH*Math.random()), -0.3+0.6*Math.random(),0,
                                30,33,0.05+0.05*Math.random(), 240, flash[(int)(Math.random()*5)])
                        );
                    }
                    GameObject.CreateObject(new ObjectParticle(this,
                            boardx+(x*main.SPR_WIDTH),boardy+y*main.SPR_WIDTH, 0,0,
                            7,10,0.05+0.05*Math.random(), 240, Color.WHITE)
                    );
                    illum = Math.random();
                    boardx += 2-(int)(Math.random()*4);
                    boardy += 2-(int)(Math.random()*4); // lighting, board shake, particle effects
                }

                // dont let clearx leave board bounds
                if(clearx >= board_bound_w*boardHeight-1){
                    if(state == STATE_LOSE && lives <= 0) state = 2; // otherwise kill the player
                    else state = STATE_PLAY;
                }else clearx++;
            }else if(clearx > 0) clearx--; // gradually reset animation

            // pause overlay
            if(state == STATE_PAUSE){ // 'pause menu'
                draw.drawText("PAUSED",(int)draw.view_x+main.FRAMEBUFFER_W/2,(int)draw.view_y+main.FRAMEBUFFER_H/2,10,8,null,1);
                draw.drawText("ESC TO RESUME",(int)draw.view_x+main.FRAMEBUFFER_W/2,(int)draw.view_y+main.FRAMEBUFFER_H/2+10,8,6,Color.GRAY,1);
            }

            if((Integer)main.cfg.get("extend") == 1){ // goblin-specific ui
                // sliding level transition initializer
                if(state != STATE_STARTLEVEL && (int)main.frame % (int)main.TPS == 0 && main.gamemode == GM.GM_OFFLINE && enemy_visible == 0){
                    state = STATE_ENDLEVEL;
                    cleardx = board_bound_w*main.SPR_WIDTH;
                }

                if(state == STATE_ENDLEVEL){ // if animation is for end of level, load next level and start level clear animation
                    level++;
                    board_bound_x += board_bound_w;
                    currentTetromino.ResetTetromino();
                    state = STATE_STARTLEVEL;
                }

                // draw pregame timer in multiplayer
                if(game_start_wait > 0) draw.drawText(""+(game_start_wait/(int)main.TPS),(int)(draw.view_x+draw.view_w/2),(int)draw.view_y+10,10,8,Color.WHITE,1);

                // ui specific to the index of the player object
                switch(playerObject.inst){
                    case 3: {
                        // tetromino object has lives and ui centered around playing tetris
                        double j = Math.min(1,(clearx*Math.log(1+clearx))/((double)board_bound_w*boardHeight)); // animation curve
                        if(state == STATE_ENDLEVEL) j = 0; // hack to not move the hearts during the level clear animation
                        //animation to move 'lives' display to center of screen anytime the player loses a life
                        int dx = (int)draw.lerp(12,boardx+(board_bound_w/2f)*main.SPR_WIDTH-12,j)+(int)(Math.random()*j*4-2*j),
                                dy = (int)draw.lerp(30,main.FRAMEBUFFER_H/2f,j)+(int)(Math.random()*j*4-2*j);
                        for(int i = 0; i < 3; i++){ // 'healthbar'
                            draw.batchPush(140,(int)draw.view_x+dx+i*(main.SPR_WIDTH+1),(int)draw.view_y+dy,main.SPR_WIDTH,main.SPR_WIDTH);
                            if(lives > i) draw.batchPush(141,(int)draw.view_x+dx+i*(main.SPR_WIDTH+1),(int)draw.view_y+dy,main.SPR_WIDTH,main.SPR_WIDTH);
                        }
                        if(j == 1){ // scary little message when you lose a life
                            String txt;
                            if(lives == 2) txt = "TRY AGAIN";
                            else if(lives == 1) txt = "CAREFUL NOW";
                            else txt = "GOODBYE";
                            int k = (int)(Math.random()*(txt.length()-1));
                            txt = txt.replace(txt.substring(k,k+1),""+('A'+(int)(Math.random()*('Z'-'A'))) ); // pretty unnecessary but this obfuscates the text
                            int w = (int)((txt.length()*10)/2f);
                            draw.batchPush(9,(int)draw.view_x+dx+12-w,(int)draw.view_y+dy+10,2*w,10,new Color(24,20,37));
                            draw.drawText(txt,(int)draw.view_x+dx+12-w,dy+10,10,10,null);
                        }
                        // level clear message
                        if(state == STATE_STARTLEVEL) draw.drawText("LEVEL CLEARED".substring(0,(int)(13*Math.min(1,((board_bound_w*main.SPR_WIDTH-cleardx)*3)/(board_bound_w*main.SPR_WIDTH)))),(int)draw.view_x+main.FRAMEBUFFER_W/2-65,(int)draw.view_y+main.FRAMEBUFFER_H/2,10,10,null);
                    } break;
                    case 2: {
                        // goblins ui is about building. this mechanic is also implemented here
                        double tb = 0;
                        int tile = 0;
                        int ui = 0;
                        int tools = 4;
                        // switch to hammer if you dont have enough resources
                        if(tool == 2 && stone <= 0) tool = 0;
                        if((tool == 3 || tool == 1) && wood <= 0) tool = 0;
                        // draw little menu for tools/tiles
                        for(int i = 0; i < tools; i++){
                            int uix = (int)(draw.view_x+main.FRAMEBUFFER_W/2-(1.5-i)*(main.SPR_WIDTH+2));
                            int uiy = (int)draw.view_y+main.FRAMEBUFFER_H/2+2*main.SPR_WIDTH;
                            draw.batchPush(156,uix,uiy,main.SPR_WIDTH,main.SPR_WIDTH);
                            draw.batchPush(164+i,uix,uiy,main.SPR_WIDTH,main.SPR_WIDTH);
                            if(tool == i) draw.batchPush(155,uix,uiy,main.SPR_WIDTH,main.SPR_WIDTH);
                            if(main.mouseInArea(uix,uiy,main.SPR_WIDTH,main.SPR_WIDTH) == 1){
                                draw.batchPush(164+i,uix,uiy,main.SPR_WIDTH,main.SPR_WIDTH,Color.DARK_GRAY);
                                ui = 1;
                                main.cursorcontext = 1;
                                if(main.input.get(-1) == 1){
                                    tool = i;
                                }
                            }
                        }
                        // draw quantity labels for your materials
                        for(int i = 0; i < tools; i++){
                            int uix = (int)(draw.view_x+main.FRAMEBUFFER_W/2-(1.5-i)*(main.SPR_WIDTH+2));
                            int uiy = (int)draw.view_y+main.FRAMEBUFFER_H/2+2*main.SPR_WIDTH;
                            if(i == 2) draw.drawText(""+stone,uix+4,uiy+4,8,6);
                            else if(i == 1 || i == 3) draw.drawText(""+wood,uix+4,uiy+4,8,6);
                        }

                        // tile breaking/placing
                        if(ui == 0){
                            // get tile to place and time to place based on tool -> scaffold is instant to place, bricks take 2 seconds, etc
                            if(tool == 0){
                                tb = 1;
                            }else if(tool == 1){
                                tile = 160;
                                tb = 0;
                            }else if(tool == 2){
                                tile = 114;
                                tb = 0.2;
                            }else if(tool == 3){
                                tile = 190;
                            }
                            // get mouse cell position
                            int mx = (int)Math.floor((main.mousex-posx)/main.SPR_WIDTH), my = (int)((main.mousey-posy)/main.SPR_WIDTH);
                            int yes = 0;
                            // check if tile to place/break is valid
                            if(mx >= 0 && mx < boardWidth && my >= 0 && my < boardHeight){
                                if(tile != 0){
                                    if((board[mx][my] == 0)){
                                        if((mx-1 >= 0 && board[mx-1][my] > 0) || (my-1 >= 0 && board[mx][my-1] > 0) || (mx+1 < boardWidth && board[mx+1][my] > 0) || (my+1 < boardHeight && board[mx][my+1] > 0) || my+1 == boardHeight){
                                            yes = 1;
                                        }
                                    }
                                }else if(board[mx][my] > 0){
                                    yes = 1;
                                }
                            }
                            if(yes==1)draw.batchPush(155,posx+mx*main.SPR_WIDTH,posy+my*main.SPR_WIDTH,main.SPR_WIDTH,main.SPR_WIDTH);
                            if(mx != tbx || my != tby){
                                tilebreak = 0;
                                tbx = mx;
                                tby = my;
                            }
                            // left mouse click
                            if(main.input.get(-1) > 0){
                                if(yes == 1){
                                    // tile is valid
                                    // litle block break/place animation
                                    if(tilebreak > 0) draw.batchPush(142+(int)(5*(tilebreak/tb)),posx+mx*main.SPR_WIDTH,posy+my*main.SPR_WIDTH,main.SPR_WIDTH,main.SPR_WIDTH);

                                    // destroy/place block, inform host about it, decrement resources
                                    tilebreak += 1/(main.TPS*2.);
                                    if(tilebreak > tb){
                                        if(board[mx][my] == 114) stone++;
                                        else if(board[mx][my] > 100) wood++;
                                        board[mx][my] = tile;
                                        ByteBuffer buffer = NetworkManager.packet_start(NPH.NET_TILE);
                                        buffer.put((byte)1);
                                        buffer.putInt(mx);
                                        buffer.putInt(my);
                                        buffer.putInt(tile);
                                        NetworkManager.send_all(buffer);
                                        tilebreak = 0;

                                        if(tool == 1){
                                            wood--;
                                        }else if(tool == 2){
                                            stone--;
                                        }else if(tool == 3){
                                            wood--;
                                        }
                                    }
                                }
                            }
                        }
                    } break;
                }
            }
        }else{ // gameover screen
            draw.view_x = 0;
            draw.view_y = 0;
            main.bgtx = 0;

            String user = (main.cfg.get("username")+control).replace(" ","");
            Integer sc = (Integer)main.scores.get(user);
            if(sc == null || score > sc){
                main.scores.put(user,score);
                main.saveData(main.scores,"src/data/highscore.json",ParseFormat.JSON);
                //System.out.println("");
            }
            if(draw.drawButton("MAIN MENU",10,40,80,10) == 1) main.currentScene = new menu(main,draw);
            if(draw.drawButton("QUIT",10,51,80,10) == 1) main.displayconfirm = 1;
            draw.drawText("GAME OVER "+user,10,30,10,10,Color.RED);
        }
        // context independent ui for level and score
        draw.drawText("LEVEL "+level+control,(int)draw.view_x+10,(int)draw.view_y+10,10,8,flash[level%5]);
        draw.drawText(""+score,(int)draw.view_x+10,(int)draw.view_y+20,8,6,flash[(score/100)%5]);

        // confirm exit dialog
        if(state == STATE_oops){
            if(main.displayconfirm != main.DIALOG_CONTEXT_MENU) state = oldstate;
        }else if(draw.drawButton("BACK",(int)draw.view_x+20,(int)draw.view_y+main.FRAMEBUFFER_H-20,80,10) == 1){
            if(state != STATE_GAMEOVER){
                oldstate = state;
                state = STATE_oops;
                main.displayconfirm = main.DIALOG_CONTEXT_MENU;
            }else main.currentScene = new menu(main,draw);
        }
    }
}
