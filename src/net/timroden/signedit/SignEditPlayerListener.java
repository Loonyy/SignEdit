package net.timroden.signedit;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.regex.Pattern;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;

import com.griefcraft.model.Protection;

public class SignEditPlayerListener implements Listener {
	public SignEdit plugin;
	Protection protection;
	Object[] data ;
	int line;
	String changetext;
	
	private static final Pattern[] patterns = {
        Pattern.compile("^$|^\\w.+$"),
        Pattern.compile("[0-9]+"),
        Pattern.compile(".+"),
        Pattern.compile("[\\w : -]+")
	};
	
	public SignEditPlayerListener(SignEdit parent) {
		this.plugin = parent;
	}
	
	@EventHandler(priority = EventPriority.HIGHEST)
	public void onPlayerInteract(PlayerInteractEvent event) {
		Player p = event.getPlayer();
		boolean canAccess = true;
		boolean csAccess = true;
		boolean fbAccess = true;
		
		data = plugin.playerLines.get(p);
		if((event.getClickedBlock() != null) && isSign(event.getClickedBlock())) {
			BlockState gs = event.getClickedBlock().getState();
			Sign sign = (Sign) gs;
			String[] lines = sign.getLines();
			if(event.getAction().equals(plugin.config.clickAction)) {
				if(plugin.playerLines.containsKey(p) && data[2] == SignFunction.COPY) {
					if(plugin.config.useLWC) {
						canAccess = plugin.performLWCCheck(p, plugin.lwc.findProtection(event.getClickedBlock()));
					}
					if(p.getGameMode().equals(GameMode.CREATIVE) && plugin.config.ignoreCreative) {
						event.setCancelled(true);
						sign.update();
					}
					if(canAccess == true || p.hasPermission("signedit.override")) {
						plugin.clipboard.put(p, sign.getLines());
						data[2]="paste";
						p.sendMessage(plugin.chatPrefix + ChatColor.GREEN + "Sign added to clipboard, punch a sign to paste.");
						
					} else {
						plugin.playerLines.remove(p);
						sign.update();
						p.sendMessage(plugin.chatPrefix + ChatColor.RED + "You do not have permission to copy that sign!");
					}
				}else if(plugin.clipboard.containsKey(p)&& data[2]== SignFunction.PASTE) {
					if(plugin.config.useLWC) {
						canAccess = plugin.performLWCCheck(p, plugin.lwc.findProtection(event.getClickedBlock()));
						p.sendMessage(plugin.chatPrefix + ChatColor.RED + "You do not have permission to paste on that sign!");
					}
					if(data[0]==null && !(data[0]=="persist")){
						if(Integer.parseInt((String) data[1]) == 0){
							p.sendMessage(plugin.chatPrefix + ChatColor.RED + "You are out of Copies!");
							data[2]="outOfInk";
						}else{
							data[1]= ""+(Integer.parseInt((String) data[1])-1);
							p.sendMessage(plugin.chatPrefix + ChatColor.GREEN + "Sign has been pasted. You have " + data[1] + " copies left.");
						}
					}else{
						p.sendMessage(plugin.chatPrefix + ChatColor.GREEN + "Sign has been pasted. Persist mode enabled.");
					}
					if((canAccess == true || p.hasPermission("signedit.override"))&& data[2]== "paste") {
						event.setCancelled(true);
						Object[] cplines = plugin.clipboard.get(p);
						sign.setLine(0, (String) cplines[0]);
						sign.setLine(1, (String) cplines[1]);
						sign.setLine(2, (String) cplines[2]);
						sign.setLine(3, (String) cplines[3]);
						sign.update();
					} else {
						plugin.playerLines.remove(p);
						sign.update();
					}
				}
				if(plugin.playerLines.containsKey(p) && data[2] == "edit") {
					if(plugin.config.useLWC) {
						canAccess = plugin.performLWCCheck(p, plugin.lwc.findProtection(event.getClickedBlock()));
					}
					if(p.getGameMode().equals(GameMode.CREATIVE) && plugin.config.ignoreCreative) {
						event.setCancelled(true);
					}
					if(canAccess == true || p.hasPermission("signedit.override")) {
						line = (Integer.parseInt((String) data[0]) - 1);
						changetext = (String) data[1];
						if(futureValid(sign, changetext, line) && !p.hasPermission("signedit.override")) {
							if (line == 0){
								csAccess = false;		
							}else{
								if (formatFirstLine(lines[0], p)) sign.setLine(0, signName(p.getName()));								
							}
						}
						if(line == 1 && changetext.contains("[MC")) {
							fbAccess = false;
						}
						if(csAccess) {
							if(fbAccess) {
								if(changetext == "") {
									p.sendMessage(plugin.chatPrefix + ChatColor.GREEN + "Line deleted.");
									sign.setLine(line, "");
									changetext = stripColourCodes(changetext);
								} else {
									p.sendMessage(plugin.chatPrefix + ChatColor.GREEN + "Line changed.");
									sign.setLine(line, ChatColor.translateAlternateColorCodes('&', changetext));
									changetext = stripColourCodes(changetext);
								}
								if(plugin.config.logEnabled == false) {
									plugin.log.info("[SignEdit] Sign Change: " + p.getName() + " changed sign at x:" + sign.getLocation().getBlockX() + " y:" + sign.getLocation().getBlockY() + " z:" + sign.getLocation().getBlockZ() + " in world " + p.getWorld().getName() + "; Line " + data[0] + " changed to \"" + changetext + "\"");
								} else {
									DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
									try {
										plugin.openFileOutput();
										plugin.fileOutput.write("[" + dateFormat.format(new Date()) + "] " + p.getName() + " changed sign at x:" + sign.getLocation().getBlockX() + " y:" + sign.getLocation().getBlockY() + " z:" + sign.getLocation().getBlockZ() + " in world " + p.getWorld().getName() + "; Line " + data[0] + " changed to \"" + changetext + "\"");
										plugin.fileOutput.newLine();
										plugin.fileOutput.close();
									} catch (IOException e) {
										e.printStackTrace();
									}
								}
								notify(plugin.chatPrefix + ChatColor.DARK_GREEN + "Sign Change: " + ChatColor.GRAY + p.getName() + ChatColor.RESET + " changed line to \"" + changetext + "\"");
								sign.update();
								plugin.playerLines.remove(p);
							} else {
								plugin.playerLines.remove(p);
								p.sendMessage(plugin.chatPrefix + ChatColor.RED + "You cannot change IC's. Access denied!");
							}
						} else {
							plugin.playerLines.remove(p);
							sign.update();
							p.sendMessage(plugin.chatPrefix + ChatColor.RED + "You cannot modify shop owners. Access denied!");
						}
					} else {
						plugin.playerLines.remove(p);
						sign.update();
						p.sendMessage(plugin.chatPrefix + ChatColor.RED + "You do not have permission to edit that sign!");
					}
				}
			}
		}
	}	
	public void notify(String message) {
	    for(Player player: Bukkit.getServer().getOnlinePlayers()) {	        
	        if(player.isPermissionSet("signedit.notify")) {
	            player.sendMessage(message);
	        }	     
	    }
	}
	public boolean isSign(Block b) {
		return (b.getType().equals(Material.SIGN) || b.getType().equals(Material.SIGN_POST) || b.getType().equals(Material.WALL_SIGN));
	}
	public static boolean futureValid(Sign sign, String changetext, Integer line){
		String [] newsign = sign.getLines().clone();
		newsign[line]=changetext;
		return isValid(newsign);
	}	
	public static boolean isValid(Sign sign) {
        return isValid(sign.getLines());
    }
    public static boolean isValid(String[] line) {
        return isValidPreparedSign(line) && (line[2].contains("B") || line[2].contains("S")) && !line[0].isEmpty();
    }
    public static boolean isValidPreparedSign(String[] lines) {
        boolean toReturn = true;
        for (int i = 0; i < 4 && toReturn; i++) toReturn = patterns[i].matcher(lines[i]).matches();
        return toReturn && lines[2].indexOf(':') == lines[2].lastIndexOf(':');
    }    
    private static boolean formatFirstLine(String line1, Player player) {
        return line1.isEmpty() || (!line1.equals(player.getName()));
    }    
    public static String stripColourCodes(String string) {
    	return string.replaceAll("&[0-9a-fA-Fk-oK-OrR]", "");        
    }
    
    public static String signName(String name) {
    	return (name.length() > 15 ? name.substring(0, 15) : name);
    }
}