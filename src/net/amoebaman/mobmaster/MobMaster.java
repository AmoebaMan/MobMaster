package net.amoebaman.mobmaster;

import java.util.*;
import java.util.Map.Entry;

import org.bukkit.*;
import org.bukkit.command.BlockCommandSender;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.*;
import org.bukkit.entity.Skeleton.SkeletonType;
import org.bukkit.entity.Villager.Profession;
import org.bukkit.entity.minecart.CommandMinecart;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import net.amoebaman.mobmaster.MobFlags.ArmorType;
import net.amoebaman.utils.CommandController;
import net.amoebaman.utils.CommandController.CommandHandler;
import net.amoebaman.utils.maps.PlayerMap;
import net.amoebaman.utils.plugin.MetricsLite;
import net.amoebaman.utils.plugin.Updater;
import net.amoebaman.utils.plugin.Updater.UpdateType;

import net.minecraft.util.com.google.common.collect.Lists;

public class MobMaster extends JavaPlugin implements Listener {
	
	private List<String> boyNames = new ArrayList<String>(), girlNames = new ArrayList<String>();
	private PlayerMap<Map<ItemStack, String>> binds = new PlayerMap<Map<ItemStack, String>>();
	
	public void onEnable() {
		
		Scanner s;
		s = new Scanner(MobMaster.plugin().getResource("boy_names.txt")).useDelimiter("\\n");
		while(s.hasNext())
			boyNames.add(s.next());
		s = new Scanner(MobMaster.plugin().getResource("girl_names.txt")).useDelimiter("\\n");
		while(s.hasNext())
			girlNames.add(s.next());
		
		Bukkit.getScheduler().scheduleSyncRepeatingTask(this, new ShooterMobs(), 0L, 4L);
		Bukkit.getPluginManager().registerEvents(this, this);
		CommandController.registerCommands(this);
		
		try { new MetricsLite(this).start(); }
		catch (Exception e) { e.printStackTrace(); }
		
		Updater.checkConfig();
		if(Updater.isEnabled())
			new Updater(this, 74552, getFile(), UpdateType.DEFAULT, true);
		
	}
	
	public static JavaPlugin plugin() { return (JavaPlugin) Bukkit.getPluginManager().getPlugin("MobMaster"); }
	
	@SuppressWarnings("deprecation")
	@CommandHandler(cmd = "mobspawn")
	public void mobspawn_cmd(final CommandSender sender, String[] args) {
		
		if (args.length < 1) {
			sender.sendMessage(ChatColor.RED + "Include at least the mob type to spawn");
			return;
		}
		
		MobFlags flags = new MobFlags();
		if (args[0].contains(":")) {
			flags.tag = args[0].split(":")[1];
			args[0] = args[0].split(":")[0];
		}
		EntityType type = Utils.matchName(args[0]);
		if (type == null || !type.isAlive() || !type.isSpawnable()) {
			sender.sendMessage(ChatColor.RED + "Mob type not recognized or not spawnable (might be too complex)");
			return;
		}
		
		Location loc = null;
		if (sender instanceof Player)
			loc = ((Player) sender).getLastTwoTargetBlocks(null, 250).get(0).getLocation().add(0.5, 0.5, 0.5);
		if (sender instanceof BlockCommandSender)
			loc = ((BlockCommandSender) sender).getBlock().getLocation().add(0.5, 1.5, 0.5);
		if (sender instanceof CommandMinecart)
			loc = ((CommandMinecart) sender).getLocation();
		
		int amount = 1;
		if (args.length >= 2) {
			try {
				amount = Integer.parseInt(args[1]);
			}
			catch (NumberFormatException nfe) {}
		}
		
		SpawnMode mode = SpawnMode.STATIC;
		
		if (args.length >= 3)
			for (int i = 2; i < args.length; i++) {
				String flag = args[i].contains(":") ? args[i].substring(0, args[i].indexOf(':')) : args[i];
				String value = args[i].contains(":") ? args[i].substring(args[i].indexOf(':') + 1) : "";
				
				if (flag.startsWith("n"))
					flags.name.addAll(Lists.newArrayList(value.replace('_', ' ').split(",")));
				PotionEffectType effect = Utils.matchPotionEffect(flag);
				if (effect != null) {
					try {
						flags.effects.put(effect, Integer.parseInt(value));
					}
					catch (Exception e) {}
				}
				if (flag.startsWith("m")) {
					mode = SpawnMode.valueOf(value.toUpperCase());
					if (mode == null)
						mode = SpawnMode.STATIC;
				}
				if (flag.startsWith("h")) {
					try {
						flags.health = Float.parseFloat(value);
					}
					catch (Exception e) {}
				}
				if (flag.startsWith("d")) {
					try {
						flags.damage = Float.parseFloat(value);
					}
					catch (Exception e) {}
				}
				if (flag.startsWith("t")) {
					if (Bukkit.getPlayer(value) != null)
						loc = Bukkit.getPlayer(value).getLocation();
					else {
						String[] split = value.split(",");
						try {
							if(loc == null)
								loc = new Location(Bukkit.getWorld(split[0]), Integer.parseInt(split[1]) + 0.5, Integer.parseInt(split[2]) + 0.5, Integer.parseInt(split[3]) + 0.5);
							else{
								loc.setX(Integer.parseInt(split[0]) + 0.5);
								loc.setY(Integer.parseInt(split[1]) + 0.5);
								loc.setZ(Integer.parseInt(split[2]) + 0.5);
							}
						}
						catch (Exception e) {}
					}
				}
				if (flag.startsWith("e")) {
					if (value.contains("f")) {
						flags.incendiary = true;
						value = value.replace("f", "");
					}
					try {
						flags.explosion = Float.parseFloat(value);
					}
					catch (Exception e) {}
				}
				if(flag.startsWith("a"))
					flags.armor = ArmorType.fromString(value);
				if(flag.startsWith("s")) {
					String[] split = value.split(",");
					if(split.length >= 2)
						try{ flags = flags.withProjectile(Utils.matchName(split[0]), Integer.parseInt(split[1])); } catch(Exception e){}
				}
				if(flag.startsWith("c"))
					try{ flags.counter = Float.parseFloat(value); } catch(Exception e){}
				if(flag.equals("bind") && sender instanceof Player && ((Player) sender).getItemInHand() != null){
					Player player = (Player) sender;
					String fullCmd = "mobspawn " + type.name() + (flags.tag.isEmpty() ? "" : ":" + flags.tag) + " " + amount;
					for(String arg : args)
						fullCmd += " " + arg;
					if(!binds.containsKey(player))
						binds.put(player, new HashMap<ItemStack, String>());
					binds.get(player).put(player.getItemInHand(), fullCmd);
				}
			}
		
		if (loc == null) {
			sender.sendMessage(ChatColor.RED + "Sorry guy, you can't spawn mobs without a reference location");
			sender.sendMessage(ChatColor.RED + "If you're on console, try adding \"t:<player>\" or \"t:(<x>,<y>,<z>)\" to the command");
			return;
		}
		
		final EntityType fType = type;
		final Location fLoc = loc;
		final MobFlags fFlags = flags;
		
		switch (mode) {
			case STATIC:
				for (int i = 0; i < amount; i++)
					spawnMob(loc, new Vector(0, 0, 0), type, flags);
				break;
			case BOMB:
				for (int i = 0; i < amount; i++)
					spawnMob(loc, new Vector(Math.random() - 0.5, Math.random() * 0.75, Math.random() - 0.5), type, flags);
				break;
			case CHAIN:
				for (int i = 0; i < amount; i++)
					Bukkit.getScheduler().scheduleSyncDelayedTask(this, new Runnable() {
						public void run() {
							Location target = fLoc;
							if (sender instanceof Player)
								target = ((Player) sender).getTargetBlock(null, 250).getLocation();
							spawnMob(target, new Vector(0, 0, 0), fType, fFlags);
						}
					}, i * 5);
				break;
			case FOUNTAIN:
				for (int i = 0; i < amount; i++)
					Bukkit.getScheduler().scheduleSyncDelayedTask(this, new Runnable() {
						public void run() {
							spawnMob(fLoc, new Vector(Math.random() - 0.5, Math.random() * 0.75, Math.random() - 0.5), fType, fFlags);
						}
					}, i * 5);
				break;
		}
		
	}
	
	@SuppressWarnings("deprecation")
	@CommandHandler(cmd = "mobkill")
	public void mobkill_cmd(CommandSender sender, String[] args) {
		Location loc = null;
		if (sender instanceof Player)
			loc = ((Player) sender).getLastTwoTargetBlocks(null, 250).get(0).getLocation().add(0.5, 0.5, 0.5);
		if (sender instanceof BlockCommandSender)
			loc = ((BlockCommandSender) sender).getBlock().getLocation().add(0.5, 1.5, 0.5);
		if (sender instanceof CommandMinecart)
			loc = ((CommandMinecart) sender).getLocation();
		
		int radius = 0;
		Map<EntityType, Boolean> which = new HashMap<EntityType, Boolean>();
		boolean monsters = true, animals = false, villagers = false, others = false;
		
		if (args.length >= 1)
			for (int i = 0; i < args.length; i++) {
				String flag = args[i].contains(":") ? args[i].substring(0, args[i].indexOf(':')) : args[i];
				String value = args[i].contains(":") ? args[i].substring(args[i].indexOf(':') + 1) : "";
				if (flag.startsWith("r")) {
					try {
						radius = Integer.parseInt(value);
					}
					catch (Exception e) {}
				}
				if (flag.startsWith("t")) {
					if (Bukkit.getPlayer(value) != null)
						loc = Bukkit.getPlayer(value).getLocation();
					else {
						String[] split = value.split(",");
						try {
							if(loc == null)
								loc = new Location(Bukkit.getWorld(split[0]), Integer.parseInt(split[1]) + 0.5, Integer.parseInt(split[2]) + 0.5, Integer.parseInt(split[3]) + 0.5);
							else{
								loc.setX(Integer.parseInt(split[0]) + 0.5);
								loc.setY(Integer.parseInt(split[1]) + 0.5);
								loc.setZ(Integer.parseInt(split[2]) + 0.5);
							}
						}
						catch (Exception e) {}
					}
				}
				if (flag.startsWith("+")) {
					if (flag.contains("m"))
						monsters = true;
					if (flag.contains("a"))
						animals = true;
					if (flag.contains("v"))
						villagers = true;
					if (flag.contains("o"))
						others = true;
				}
				if (flag.startsWith("-")) {
					if (flag.contains("m"))
						monsters = false;
					if (flag.contains("a"))
						animals = false;
					if (flag.contains("v"))
						villagers = false;
					if (flag.contains("o"))
						others = false;
				}
				if (Utils.matchName(flag) != null) {
					try {
						which.put(Utils.matchName(flag), Boolean.parseBoolean(value));
					}
					catch (Exception e) {}
				}
			}
		
		if (loc == null) {
			sender.sendMessage(ChatColor.RED + "Sorry guy, you can't slaughter mobs without a reference location");
			sender.sendMessage(ChatColor.RED + "If you're on console, try adding \"t:<player>\" or \"t:(<x>,<y>,<z>)\" to the command");
			return;
		}
		
		int count = 0;
		for (LivingEntity e : loc.getWorld().getEntitiesByClass(LivingEntity.class))
			if (!(e instanceof Player))
				if (radius == 0 ^ e.getLocation().distance(loc) < radius) {
					boolean remove;
					if (which.containsKey(e.getType()))
						remove = which.get(e.getType());
					else
						remove = e instanceof Monster && monsters || e instanceof Animals && animals || e instanceof Villager && villagers || others && !(e instanceof Monster)
						        && !(e instanceof Animals) && !(e instanceof Villager);
					if (remove) {
						e.remove();
						count++;
					}
				}
		sender.sendMessage("Slaughtered " + count + " mobs");
	}
	
	public void spawnMob(Location loc, Vector vel, EntityType type, MobFlags flags) {
		if (!(type.isSpawnable() && type.isAlive()))
			return;
		LivingEntity e = (LivingEntity) loc.getWorld().spawnEntity(loc, type);
		e.setCanPickupItems(true);
		if (vel.length() > 0){
			e.teleport(loc);
			e.setVelocity(vel);
		}
		if (!flags.name.isEmpty()) {
			e.setCustomName(flags.name.get(new Random().nextInt(flags.name.size())));
			if (e.getCustomName().contains("rand")) {
				List<String> options;
				if(e.getCustomName().contains("b") ^ e.getCustomName().contains("g"))
					options = e.getCustomName().contains("b") ? boyNames : girlNames;
				else
					options = Math.random() > 0.5 ? boyNames : girlNames;
				e.setCustomName(options.get(new Random().nextInt(options.size())));
			}
			e.setCustomNameVisible(true);
			e.setRemoveWhenFarAway(false);
		}
		if (e instanceof Ageable && flags.tag.contains("baby")) {
			((Ageable) e).setBaby();
			((Ageable) e).setAgeLock(true);
		}
		if (e instanceof Zombie) {
			if (flags.tag.contains("baby"))
				((Zombie) e).setBaby(true);
			if (flags.tag.contains("vill"))
				((Zombie) e).setVillager(true);
		}
		if (e instanceof Creeper && (flags.tag.contains("charged") || flags.tag.contains("powered")))
			((Creeper) e).setPowered(true);
		if (e instanceof Pig && flags.tag.contains("saddle"))
			((Pig) e).setSaddle(true);
		if (e instanceof PigZombie && flags.tag.contains("angry"))
			((PigZombie) e).setAngry(true);
		if (e instanceof Sheep){
			if(flags.tag.contains("shear"))
				((Sheep) e).setSheared(true);
			if(flags.tag.contains("rand"))
				((Sheep) e).setColor(DyeColor.values()[new Random().nextInt(DyeColor.values().length)]);
			else
				for(DyeColor color : DyeColor.values())
					if(flags.tag.contains(color.name().toLowerCase()))
						((Sheep) e).setColor(color);
		}
		if (e instanceof Skeleton && flags.tag.contains("wither"))
			((Skeleton) e).setSkeletonType(SkeletonType.WITHER);
		if (e instanceof Slime) {
			try {
				((Slime) e).setSize(Integer.parseInt(flags.tag));
			}
			catch (Exception ex) {}
		}
		if (e instanceof Villager) {
			if (flags.tag.contains("rand"))
				((Villager) e).setProfession(Profession.values()[new Random().nextInt(Profession.values().length)]);
			else {
				try {
					((Villager) e).setProfession(Profession.valueOf(flags.tag.toUpperCase()));
				}
				catch (Exception ex) {}
			}
		}
		
		if(flags.armor != null){
			EntityEquipment equip = e.getEquipment();
			equip.setHelmet(new ItemStack(Material.matchMaterial(flags.armor.name() + "_HELMET")));
			equip.setChestplate(new ItemStack(Material.matchMaterial(flags.armor.name() + "_CHESTPLATE")));
			equip.setLeggings(new ItemStack(Material.matchMaterial(flags.armor.name() + "_LEGGINGS")));
			equip.setBoots(new ItemStack(Material.matchMaterial(flags.armor.name() + "_BOOTS")));
		}
		
		for (Entry<PotionEffectType, Integer> entry : flags.effects.entrySet())
			e.addPotionEffect(new PotionEffect(entry.getKey(), Integer.MAX_VALUE, entry.getValue()));
		
		e.setMaxHealth(e.getMaxHealth() * flags.health);
		e.setHealth(e.getMaxHealth());
		
		e.setMetadata("master-mob", new FixedMetadataValue(this, true));
		e.setMetadata("mob-flags", new FixedMetadataValue(this, flags));
	}
	
	@EventHandler
	public void debugMobFlags(PlayerInteractEntityEvent event){
		if(event.getPlayer().getItemInHand().getType() == Material.RED_MUSHROOM && Utils.isMasterMob(event.getRightClicked()))
			event.getPlayer().sendMessage("[TICK_" + ShooterMobs.tick + "] " + ((MobFlags) event.getRightClicked().getMetadata("mob-flags").get(0).value()).toString());
	}
	
	@EventHandler
	public void applyDamageModifier(final EntityDamageEvent event) {
		if (event instanceof EntityDamageByEntityEvent) {
			Entity e = ((EntityDamageByEntityEvent) event).getDamager();
			if (e instanceof Projectile && ((Projectile) e).getShooter() instanceof Entity)
				e = (Entity) ((Projectile) e).getShooter();
			if (e instanceof LivingEntity && Utils.isMasterMob(e))
				event.setDamage(event.getDamage() * ((MobFlags) e.getMetadata("mob-flags").get(0).value()).damage);
			final Entity d = e;
			final Entity v = event.getEntity();
			if(Utils.isMasterMob(v) && d instanceof LivingEntity)
				Bukkit.getScheduler().scheduleSyncDelayedTask(this, new Runnable(){ public void run(){
					((LivingEntity) d).damage(event.getDamage() * ((MobFlags) v.getMetadata("mob-flags").get(0).value()).counter, v);
				}}, 2L);
		}
	}
	
	@EventHandler
	public void combustibleMobs(EntityDeathEvent event) {
		LivingEntity e = event.getEntity();
		if (Utils.isMasterMob(e)) {
			MobFlags flags = (MobFlags) e.getMetadata("mob-flags").get(0).value();
			if (flags.explosion > 0)
				e.getWorld().createExplosion(e.getLocation(), flags.explosion, flags.incendiary);
		}
	}
	
	public static class ShooterMobs implements Runnable {
		
		static int tick = 0;
		public void run(){
			
			for(World world : Bukkit.getWorlds())
				for(final LivingEntity e : world.getLivingEntities())
					if(Utils.isMasterMob(e)){
						final MobFlags flags = (MobFlags) e.getMetadata("mob-flags").get(0).value();
						if(flags.projectile != null && flags.rateOfFire >  0 && tick % flags.rateOfFire == 0)
							Bukkit.getScheduler().scheduleSyncDelayedTask(MobMaster.plugin(), new Runnable(){ public void run(){
								e.launchProjectile((Class<? extends Projectile>)flags.projectile.getEntityClass());
							}}, (long) (Math.random() * 4));
					}
			
			tick++;
			if(tick > 50)
				tick = 0;
			
		}
	}
	
}
