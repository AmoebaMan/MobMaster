package net.amoebaman.mobmaster;

import java.util.List;

import org.bukkit.ChatColor;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Entity;
import org.bukkit.potion.PotionEffectType;

@SuppressWarnings("deprecation")
public class Utils {
	
    public static EntityType matchName(String name){
		name = name.toUpperCase().replace(' ', '_');
		
		try{ return EntityType.fromId(Integer.parseInt(name)); }
		catch(NumberFormatException nfe){}
		
		return EntityType.fromName(name);
	}
	
	public static boolean isMasterMob(Entity e){
		return e.hasMetadata("master-mob");
	}
	
	public static String makeProgressBar(int length, int total, List<ChatColor> colors, List<Integer> values){
		if(colors.size() < values.size())
			return ChatColor.DARK_GRAY + "[" + ChatColor.DARK_RED + "ERROR" + ChatColor.DARK_GRAY + "]";
		String bar = ChatColor.DARK_GRAY + "[";
		int pipes = 0;
		for(int i = 0; i < values.size(); i++){
			bar += colors.get(i);
			for(int j = 0; j < (1f * values.get(i) / total) * length; j++){
				bar += "|";
				pipes++;
			}
		}
		if(pipes < length && colors.size() > values.size()){
			bar += colors.get(values.size());
			for(int i = pipes; i < length; i++)
				bar += "|";
		}
		bar += ChatColor.DARK_GRAY + "]";
		return bar;
	}
	
	public static PotionEffectType matchPotionEffect(String name){
		name = name.toLowerCase();
		if(name.contains("regen")) return PotionEffectType.REGENERATION;
		if(name.contains("poison")) return PotionEffectType.POISON;
		if(name.contains("strength")) return PotionEffectType.INCREASE_DAMAGE;
		if(name.contains("weak")) return PotionEffectType.WEAKNESS;
		if(name.contains("heal") && name.contains("boost")) return PotionEffectType.HEALTH_BOOST;
		if(name.contains("heal")) return PotionEffectType.HEAL;
		if(name.contains("harm")) return PotionEffectType.HARM;
		if(name.contains("abs")) return PotionEffectType.ABSORPTION;
		if(name.contains("speed") || name.contains("swift")) return PotionEffectType.SPEED;
		if(name.contains("slow")) return PotionEffectType.SLOW;
		if(name.contains("haste")) return PotionEffectType.FAST_DIGGING;
		if(name.contains("fat")) return PotionEffectType.SLOW_DIGGING;
		if(name.contains("hung")) return PotionEffectType.HUNGER;
		if(name.contains("resist")) return PotionEffectType.DAMAGE_RESISTANCE;
		if(name.contains("blind")) return PotionEffectType.BLINDNESS;
		if(name.contains("confus") || name.contains("naus")) return PotionEffectType.CONFUSION;
		if(name.contains("fire")) return PotionEffectType.FIRE_RESISTANCE;
		if(name.contains("jump")) return PotionEffectType.JUMP;
		if(name.contains("water") || name.contains("aqua")) return PotionEffectType.WATER_BREATHING;
		if(name.contains("invis")) return PotionEffectType.INVISIBILITY;
		if(name.contains("night")) return PotionEffectType.NIGHT_VISION;
		if(name.contains("wither")) return PotionEffectType.WITHER;
		return PotionEffectType.getByName(name.toUpperCase().replace(' ', '_'));
	}
	
}
