package net.amoebaman.mobmaster;

import java.util.*;

import org.bukkit.potion.PotionEffectType;

public class MobFlags {
	
	public String tag;
	public List<String> name;
	public Map<PotionEffectType, Integer> effects;
	public float health, damage, explosion;
	public boolean incendiary;
	
	public MobFlags(){
		tag = "";
		name = new ArrayList<String>();
		effects = new HashMap<PotionEffectType, Integer>();
		health = 1f;
		damage = 1f;
		explosion = 0f;
		incendiary = false;
	}
	
	public String toString(){
		return "name:" + name + ",effects:" + effects + ",health=" + health + ",damage=" + damage + ",explode=" + explosion + (incendiary ? "f" : "e");
	}
	
}
