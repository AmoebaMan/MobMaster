package net.amoebaman.mobmaster;

import java.util.*;
import java.util.Map.Entry;

import net.minecraft.util.com.google.common.collect.Lists;

import org.bukkit.*;
import org.bukkit.util.Vector;
import org.bukkit.command.*;
import org.bukkit.entity.*;
import org.bukkit.entity.Skeleton.SkeletonType;
import org.bukkit.entity.Villager.Profession;
import org.bukkit.entity.minecart.CommandMinecart;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class MobMaster extends JavaPlugin implements Listener, CommandExecutor{
	
	public void onEnable(){
		Bukkit.getPluginManager().registerEvents(this, this);
	}
	
	public static JavaPlugin plugin(){
		return (JavaPlugin) Bukkit.getPluginManager().getPlugin("MobMaster");
	}
	
	@SuppressWarnings("deprecation")
	public boolean onCommand(final CommandSender sender, Command command, String label, String[] args){
		
		if(sender instanceof ConsoleCommandSender || sender instanceof RemoteConsoleCommandSender){
			sender.sendMessage(ChatColor.RED + "MobMaster cannot be operated from the console");
			return true;
		}
		
		if(command.getName().equals("mobspawn")){
			
			if(args.length < 1){
				sender.sendMessage(ChatColor.RED + "Include at least the mob type to spawn");
				return true;
			}
			
			MobFlags flags = new MobFlags();
			if(args[0].contains(":")){
				flags.tag = args[0].split(":")[1];
				args[0] = args[0].split(":")[0];
			}
			EntityType type = Utils.matchName(args[0]);
			if(type == null || !type.isAlive() || !type.isSpawnable()){
				sender.sendMessage(ChatColor.RED + "Mob type not recognized or not available");
				return true;
			}
			
			Location loc = null;
			if(sender instanceof Player)
				loc = ((Player) sender).getLastTwoTargetBlocks(null, 250).get(0).getLocation().add(0.5, 0.5, 0.5);
			if(sender instanceof BlockCommandSender)
				loc = ((BlockCommandSender) sender).getBlock().getLocation().add(0.5, 1.5, 0.5);
			if(sender instanceof CommandMinecart)
				loc = ((CommandMinecart) sender).getLocation();
			
			int amount = 1;
			if(args.length >= 2){
				try{ amount = Integer.parseInt(args[1]); }
				catch(NumberFormatException nfe){}
			}
			
			SpawnMode mode = SpawnMode.STATIC;
			
			if(args.length >= 3)
				for(int i = 2; i < args.length; i++){
					String flag = args[i].contains(":") ? args[i].substring(0, args[i].indexOf(':')) : args[i];
					String value = args[i].contains(":") ? args[i].substring(args[i].indexOf(':') + 1) : "";
					
					if(flag.startsWith("n"))
						flags.name.addAll(Lists.newArrayList(value.replace('_', ' ').split(",")));
					PotionEffectType effect = Utils.matchPotionEffect(flag);
					if(effect != null){
						try{ flags.effects.put(effect, Integer.parseInt(value)); }
						catch(Exception e){}
					}
					if(flag.startsWith("m")){
						mode = SpawnMode.valueOf(value.toUpperCase());
						if(mode == null)
							mode = SpawnMode.STATIC;
					}
					if(flag.startsWith("h")){
						try{ flags.health = Float.parseFloat(value); }
						catch(Exception e){}
					}
					if(flag.startsWith("d")){
						try{ flags.damage = Float.parseFloat(value); }
						catch(Exception e){}
					}
					if(flag.startsWith("t")){
						if(Bukkit.getPlayer(value) != null)
							loc = Bukkit.getPlayer(value).getLocation();
						else{
							String[] split = value.split(",");
							try{
								loc.setX(Integer.parseInt(split[0]) + 0.5);
								loc.setY(Integer.parseInt(split[1]) + 0.5);
								loc.setZ(Integer.parseInt(split[2]) + 0.5);
							}
							catch(Exception e){}
						}
					}
					if(flag.startsWith("e")){
						if(value.contains("f")){
							flags.incendiary = true;
							value = value.replace("f", "");
						}
						try{
							flags.explosion = Integer.parseInt(value);
						}
						catch(Exception e){}
					}
				}
			
			final EntityType fType = type;
			final Location fLoc = loc;
			final MobFlags fFlags = flags;
			
			switch(mode){
				case STATIC:
					for(int i=0; i<amount; i++)
						spawnMob(loc, new Vector(0,0,0), type, flags);
					break;
				case BOMB:
					for(int i=0; i<amount; i++)
						spawnMob(loc, new Vector((Math.random()-0.5)*1, Math.random(), (Math.random()-0.5)*1), type, flags);
					break;
				case CHAIN:
					for(int i=0; i<amount; i++)
						Bukkit.getScheduler().scheduleSyncDelayedTask(this, new Runnable(){ public void run(){
							Location target = fLoc;
							if(sender instanceof Player)
								target = ((Player) sender).getTargetBlock(null, 250).getLocation();
							spawnMob(target, new Vector(0,0,0), fType, fFlags);
						}}, i*5);
					break;
				case FOUNTAIN:
					for(int i=0; i<amount; i++)
						Bukkit.getScheduler().scheduleSyncDelayedTask(this, new Runnable(){ public void run(){
							spawnMob(fLoc, new Vector((Math.random()-0.5)*1, Math.random(), (Math.random()-0.5)*1), fType, fFlags);
						}}, i*5);
					break;
			}
			
		}
		
		if(command.getName().equalsIgnoreCase("mobkill")){
			Location loc = null;
			if(sender instanceof Player)
				loc = ((Player) sender).getLastTwoTargetBlocks(null, 250).get(0).getLocation().add(0.5, 0.5, 0.5);
			if(sender instanceof BlockCommandSender)
				loc = ((BlockCommandSender) sender).getBlock().getLocation().add(0.5, 1.5, 0.5);
			if(sender instanceof CommandMinecart)
				loc = ((CommandMinecart) sender).getLocation();
			
			int radius = 0;
			Map<EntityType, Boolean> which = new HashMap<EntityType, Boolean>();
			boolean monsters = true, animals = false, villagers = false, others = false;
			
			if(args.length >= 1)
				for(int i = 0; i < args.length; i++){
					String flag = args[i].contains(":") ? args[i].substring(0, args[i].indexOf(':')) : args[i];
					String value = args[i].contains(":") ? args[i].substring(args[i].indexOf(':') + 1) : "";
					if(flag.startsWith("r")){
						try{ radius = Integer.parseInt(value); }
						catch(Exception e){}
					}
					if(flag.startsWith("t")){
						if(Bukkit.getPlayer(value) != null)
							loc = Bukkit.getPlayer(value).getLocation();
						else{
							String[] split = value.split(",");
							try{
								loc.setX(Integer.parseInt(split[0]) + 0.5);
								loc.setY(Integer.parseInt(split[1]) + 0.5);
								loc.setZ(Integer.parseInt(split[2]) + 0.5);
							}
							catch(Exception e){}
						}
					}
					if(flag.startsWith("+")){
						if(flag.contains("m"))
							monsters = true;
						if(flag.contains("a"))
							animals = true;
						if(flag.contains("v"))
							villagers = true;
						if(flag.contains("o"))
							others = true;
					}
					if(flag.startsWith("-")){
						if(flag.contains("m"))
							monsters = false;
						if(flag.contains("a"))
							animals = false;
						if(flag.contains("v"))
							villagers = false;
						if(flag.contains("o"))
							others = false;
					}
					if(Utils.matchName(flag) != null){
						try{ which.put(Utils.matchName(flag), Boolean.parseBoolean(value)); }
						catch(Exception e){}
					}
				}
			
			int count = 0;
			for(LivingEntity e : loc.getWorld().getEntitiesByClass(LivingEntity.class))
				if(!(e instanceof Player))
					if(radius == 0 ^ e.getLocation().distance(loc) < radius){
						boolean remove;
						if(which.containsKey(e.getType()))
							remove = which.get(e.getType());
						else
							remove = e instanceof Monster && monsters || e instanceof Animals && animals || e instanceof Villager && villagers
							|| others && !(e instanceof Monster) && !(e instanceof Animals) && !(e instanceof Villager);
						if(remove){
							e.remove();
							count++;
						}
					}
			sender.sendMessage("Slaughtered " + count + " mobs");
		}
		
		return true;
	}
	
	public void spawnMob(Location loc, Vector vel, EntityType type, MobFlags flags){
		if(!(type.isSpawnable() && type.isAlive()))
			return;
		LivingEntity e = (LivingEntity) loc.getWorld().spawnEntity(loc, type);
		e.setCanPickupItems(true);
		if(vel.length() > 0)
			e.setVelocity(vel);
		if(!flags.name.isEmpty()){
			e.setCustomName(flags.name.get(new Random().nextInt(flags.name.size())));
			if(e.getCustomName().contains("rand")){
				List<String> rand = Utils.getRandomNames();
				e.setCustomName(rand.get(new Random().nextInt(rand.size())));
			}
			e.setCustomNameVisible(true);
			e.setRemoveWhenFarAway(false);
		}
		if(e instanceof Ageable && flags.tag.contains("baby")){
			((Ageable) e).setBaby();
			((Ageable) e).setAgeLock(true);
		}
		if(e instanceof Zombie){
			if(flags.tag.contains("baby"))
				((Zombie) e).setBaby(true);
			if(flags.tag.contains("vill"))
				((Zombie) e).setVillager(true);
		}
		if(e instanceof Creeper && (flags.tag.contains("charged") || flags.tag.contains("powered")))
			((Creeper) e).setPowered(true);
		if(e instanceof Pig && flags.tag.contains("saddle"))
			((Pig) e).setSaddle(true);
		if(e instanceof PigZombie && flags.tag.contains("angry"))
			((PigZombie) e).setAngry(true);
		if(e instanceof Sheep && flags.tag.contains("sheared"))
			((Sheep) e).setSheared(true);
		if(e instanceof Skeleton && flags.tag.contains("wither"))
			((Skeleton) e).setSkeletonType(SkeletonType.WITHER);
		if(e instanceof Slime && flags.tag != null){
			try{ ((Slime) e).setSize(Integer.parseInt(flags.tag)); }
			catch(Exception ex){}
		}
		if(e instanceof Villager && flags.tag != null){
			if(flags.tag.contains("rand"))
				((Villager) e).setProfession(Profession.values()[new Random().nextInt(Profession.values().length)]);
			else{
				try{ ((Villager) e).setProfession(Profession.valueOf(flags.tag.toUpperCase())); }
				catch(Exception ex){}
			}
		}
		
		for(Entry<PotionEffectType, Integer> entry : flags.effects.entrySet())
			e.addPotionEffect(new PotionEffect(entry.getKey(), Integer.MAX_VALUE, entry.getValue()));
		
		e.setMaxHealth(e.getMaxHealth() * flags.health);
		e.setHealth(e.getMaxHealth());
		
		e.setMetadata("master-mob", new FixedMetadataValue(this, true));
		e.setMetadata("mob-flags", new FixedMetadataValue(this, flags));
	}
	
	@EventHandler
	public void applyDamageModifier(EntityDamageEvent event){
		if(event instanceof EntityDamageByEntityEvent){
			Entity e = ((EntityDamageByEntityEvent) event).getDamager();
			if(e instanceof Projectile)
				e = ((Projectile) e).getShooter();
			if(e instanceof LivingEntity && Utils.isMasterMob(e))
				event.setDamage(event.getDamage() * ((MobFlags) e.getMetadata("mob-flags").get(0).value()).damage);
		}
	}
	
	@EventHandler
	public void combustibleMobs(EntityDeathEvent event){
		LivingEntity e = event.getEntity();
		if(Utils.isMasterMob(e)){
			MobFlags flags = (MobFlags) e.getMetadata("mob-flags").get(0).value();
			if(flags.explosion > 0)
				e.getWorld().createExplosion(e.getLocation(), flags.explosion, flags.incendiary);
		}
	}
	
	@EventHandler
	public void debugger(PlayerInteractEntityEvent event){
		if(event.getPlayer().getItemInHand().getType() == Material.RED_MUSHROOM && event.getPlayer().isOp()){
			getLogger().info("" + event.getRightClicked().getMetadata("master-mob").get(0).value());
			getLogger().info("" + event.getRightClicked().getMetadata("mob-flags").get(0).value());
		}
	}
	
}
