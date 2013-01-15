package com.sk89q.craftbook.circuits.jinglenote;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiUnavailableException;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import com.sk89q.craftbook.bukkit.CircuitCore;
import com.sk89q.craftbook.bukkit.CraftBookPlugin;
import com.sk89q.craftbook.util.GeneralUtil;

public class Playlist {

    String playlist;

    protected volatile HashSet<Player> players; // Super safe code here.. this is going to be done across threads.
    private volatile HashSet<Player> lastPlayers;

    int position;

    List<String> lines = new ArrayList<String>();

    BukkitTask task;

    volatile JingleNoteManager jNote = new JingleNoteManager();
    volatile MidiJingleSequencer midiSequencer;
    volatile StringJingleSequencer stringSequencer;

    public Playlist(String name) {

        players = new HashSet<Player>();
        lastPlayers = new HashSet<Player>();
        playlist = name;
        try {
            readPlaylist();
        } catch (IOException e) {
            Bukkit.getLogger().severe(GeneralUtil.getStackTrace(e));
        }
    }

    public void readPlaylist() throws IOException {

        lines.clear();
        File file = new File(new File(CircuitCore.inst().getMidiFolder(), "playlists"), playlist + ".txt");
        if(!file.exists()) {
            Bukkit.getLogger().severe("Playlist File Not Found! " + file.getName());
            return;
        }
        BufferedReader br = new BufferedReader(new FileReader(file));
        String line = "";
        while ((line = br.readLine()) != null) {

            if(line.trim().isEmpty())
                continue;
            lines.add(line);
        }

        br.close();
    }

    public void startPlaylist() {

        position = 0;
        if (task != null)
            task.cancel();
        Runnable show = new PlaylistInterpreter();
        task = Bukkit.getScheduler().runTaskAsynchronously(CraftBookPlugin.inst(), show);
    }

    public void stopPlaylist() {

        lastPlayers.clear();
        jNote.stopAll();
        players.clear();
        position = 0;
        if (task != null)
            task.cancel();
    }

    @SuppressWarnings("unchecked")
    public void setPlayers(List<Player> players) {

        lastPlayers = (HashSet<Player>) this.players.clone();
        this.players.clear();
        this.players.addAll(players);
    }

    @SuppressWarnings("unchecked")
    public void addPlayers(List<Player> players) {

        lastPlayers = (HashSet<Player>) this.players.clone();
        this.players.addAll(players);
    }

    @SuppressWarnings("unchecked")
    public void removePlayers(List<Player> players) {

        lastPlayers = (HashSet<Player>) this.players.clone();
        this.players.removeAll(players);
    }

    private class PlaylistInterpreter implements Runnable {


        public PlaylistInterpreter() {

        }

        @SuppressWarnings("unchecked")
        @Override
        public void run () {

            while (position < lines.size()) {

                if(midiSequencer != null) {

                    while(midiSequencer.isSongPlaying()) {

                        if(!areIdentical(players, lastPlayers)) {

                            Bukkit.getLogger().severe("OUT OF SYNC PLAYERS");
                            for(Player p : players) {

                                if(lastPlayers.contains(p))
                                    continue;
                                Bukkit.getLogger().severe("ADDING A PLAYER");

                                jNote.play(p, midiSequencer);
                            }

                            for(Player p : lastPlayers) {

                                if(players.contains(p))
                                    continue;
                                Bukkit.getLogger().severe("TAKING A PLAYER");

                                jNote.stop(p);
                            }

                            lastPlayers = (HashSet<Player>) players.clone();
                        }

                        try {
                            Thread.sleep(1000L);
                        } catch (InterruptedException e) {
                            Bukkit.getLogger().severe(GeneralUtil.getStackTrace(e));
                        }
                    }
                    midiSequencer = null;
                }
                if(stringSequencer != null) {

                    while(stringSequencer.isSongPlaying()) {

                        if(!lastPlayers.equals(players)) {

                            for(Player p : players) {

                                if(lastPlayers.contains(p))
                                    continue;

                                jNote.play(p, stringSequencer);
                            }

                            for(Player p : lastPlayers) {

                                if(players.contains(p))
                                    continue;

                                jNote.stop(p);
                            }

                            lastPlayers = (HashSet<Player>) players.clone();
                        }

                        try {
                            Thread.sleep(1000L);
                        } catch (InterruptedException e) {
                            Bukkit.getLogger().severe(GeneralUtil.getStackTrace(e));
                        }
                    }
                    stringSequencer = null;
                }
                String line = lines.get(position);
                Bukkit.getLogger().severe(line);
                position++;
                if (line.trim().startsWith("#") || line.trim().isEmpty())
                    continue;

                if (line.startsWith("wait ")) {

                    PlaylistInterpreter show = new PlaylistInterpreter();
                    task = Bukkit.getScheduler().runTaskLaterAsynchronously(CraftBookPlugin.inst(), show, Long.parseLong(line.replace("wait ", "")));
                    return;
                } else if (line.startsWith("midi ")) {

                    File file = null;
                    String midiName = line.replace("midi ", "");

                    File[] trialPaths = {
                            new File(CircuitCore.inst().getMidiFolder(), midiName),
                            new File(CircuitCore.inst().getMidiFolder(), midiName + ".mid"),
                            new File(CircuitCore.inst().getMidiFolder(), midiName + ".midi"),
                            new File("midi", midiName), new File("midi", midiName + ".mid"),
                            new File("midi", midiName + ".midi"),
                    };

                    for (File f : trialPaths) {
                        if (f.exists()) {
                            file = f;
                            break;
                        }
                    }

                    try {
                        midiSequencer = new MidiJingleSequencer(file);
                        midiSequencer.getSequencer().start();
                    } catch (MidiUnavailableException e) {
                        Bukkit.getLogger().severe(GeneralUtil.getStackTrace(e));
                    } catch (InvalidMidiDataException e) {
                        Bukkit.getLogger().severe(GeneralUtil.getStackTrace(e));
                    } catch (IOException e) {
                        Bukkit.getLogger().severe(GeneralUtil.getStackTrace(e));
                    }

                    for(Player player : players)
                        jNote.play(player, midiSequencer);
                } else if (line.startsWith("tune ")) {

                    String tune = line.replace("tune ", "");

                    stringSequencer = new StringJingleSequencer(tune, 0);

                    for(Player player : players)
                        jNote.play(player, stringSequencer);
                } else if (line.startsWith("send ")) {

                    String message = line.replace("send ", "");

                    for(Player player : players)
                        player.sendMessage(message);
                } else if (line.startsWith("goto ")) {

                    position = Integer.parseInt(line.replace("goto ", ""));
                }
            }
        }
    }

    public boolean areIdentical(HashSet<?> h1, HashSet<?> h2) {
        if ( h1.size() != h2.size() ) {
            return false;
        }
        HashSet<?> clone = (HashSet<?>) h2.clone();
        Iterator<?> it = h1.iterator();
        while (it.hasNext() ){
            Object o = it.next();
            if (clone.contains(o)){
                clone.remove(o);
            } else {
                return false;
            }
        }
        return true;
    }
}
