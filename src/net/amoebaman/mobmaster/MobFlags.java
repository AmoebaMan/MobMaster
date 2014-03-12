package net.amoebaman.mobmaster;

import java.util.*;

import org.bukkit.entity.EntityType;
import org.bukkit.entity.Projectile;
import org.bukkit.potion.PotionEffectType;

public class MobFlags {
	
	public String tag;
	public List<String> name;
	public Map<PotionEffectType, Integer> effects;
	public float health, damage, counter, explosion;
	public boolean incendiary;
	public EntityType projectile;
	public int rateOfFire;
	public ArmorType armor;
	
	public MobFlags(){
		tag = "";
		name = new ArrayList<String>();
		effects = new HashMap<PotionEffectType, Integer>();
		health = 1f;
		damage = 1f;
		counter = 0f;
		explosion = 0f;
		rateOfFire = 0;
		incendiary = false;
		projectile = null;
		armor = null;
	}
	
	public MobFlags withNames(String... names){
		for(String name : names)
			this.name.add(name);
		return this;
	}
	
	public MobFlags withEffect(PotionEffectType type, Integer amplifier){
		effects.put(type, amplifier);
		return this;
	}
	
	public MobFlags withProjectile(EntityType type, int rateOfFire){
		if(Projectile.class.isAssignableFrom(type.getEntityClass()))
			projectile = type;
		this.rateOfFire = rateOfFire;
		return this;
	}
	
	public MobFlags withTag(String tag){ this.tag = tag; return this; }
	public MobFlags withHealth(float health){ this.health = health; return this; }
	public MobFlags withDamage(float damage){ this.damage = damage; return this; }
	public MobFlags withCounter(float counter){ this.counter = counter; return this; }
	public MobFlags withExplosion(float explosion, boolean incendiary){ this.explosion = explosion; this.incendiary = incendiary; return this; }
	public MobFlags withArmor(ArmorType armor){ this.armor = armor; return this; }
	
	public String toString(){
		return "name:" + name + ",effects:" + effects + ",health=" + health + ",damage=" + damage + ",counter=" + counter + ",explode=" + explosion + (incendiary ? "f" : "e") + ",projectile=" + projectile + "," + rateOfFire + ",armor=" + armor;
	}
	
	protected enum ArmorType {
		
		LEATHER, GOLD, CHAINMAIL, IRON, DIAMOND;
		
		public static ArmorType fromString(String str){
			str = str.toLowerCase();
			for(ArmorType type : values())
				if(str.equals(type.name().toLowerCase()))
					return type;
			return null;
		}
		
	}
	
}
