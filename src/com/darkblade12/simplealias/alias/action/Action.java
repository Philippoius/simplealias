package com.darkblade12.simplealias.alias.action;

import java.util.Arrays;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.darkblade12.simplealias.SimpleAlias;
import com.darkblade12.simplealias.alias.Executable;
import com.darkblade12.simplealias.hook.types.FactionsHook;
import com.darkblade12.simplealias.hook.types.VaultHook;
import com.darkblade12.simplealias.nameable.Nameable;
import com.darkblade12.simplealias.permission.Permission;
import com.darkblade12.simplealias.util.StringReplacer;

public abstract class Action implements Nameable, Executable, Comparable<Action> {
	private static final Pattern PARAMS_PATTERN = Pattern.compile("<params@\\d+>|<params@\\d?-\\d+>|<params@\\d+-\\d?>", Pattern.CASE_INSENSITIVE);
	private static final String REPLACE_REGEX = "\\s?<sender_name>|\\s?<params>|\\s?<params@\\d+>|\\s?<params@\\d?-\\d+>|\\s?<params@\\d+-\\d?>|\\s?<world_name>|\\s?<money_balance>|\\s?<group_name>|\\s?<faction_name>";
	protected final String name;
	protected Set<String> enabledWorlds;
	protected Set<String> enabledPermissionNodes;
	protected Set<String> enabledPermissionGroups;
	protected Map<Integer, String> enabledParams;
	protected int priority;
	protected boolean translateColorCodes;

	public Action(String name, Set<String> enabledWorlds, Set<String> enabledPermissionNodes, Set<String> enabledPermissionGroups, Map<Integer, String> enabledParams, int priority, boolean translateColorCodes) {
		this.name = name;
		this.enabledWorlds = enabledWorlds;
		this.enabledPermissionNodes = enabledPermissionNodes;
		this.enabledPermissionGroups = enabledPermissionGroups;
		this.enabledParams = enabledParams;
		this.priority = priority;
		this.translateColorCodes = translateColorCodes;
	}

	protected final String applyReplacement(String target, CommandSender sender, String[] params) {
		StringReplacer s = new StringReplacer(new String[] { "<sender_name>" }, new String[] { sender.getName() });
		if (params.length > 0) {
			s.addReplacement("<params>", StringUtils.join(params, " "));
			Matcher m = PARAMS_PATTERN.matcher(target);
			while (m.find()) {
				String param = m.group();
				String modifiers = param.substring(8, param.length() - 1);
				int seperatorIndex = modifiers.indexOf('-');
				if (seperatorIndex == -1) {
					int index = Integer.parseInt(modifiers);
					if (index < params.length)
						s.addReplacement("<params@" + index + ">", params[index]);
				} else {
					int rangeStart = seperatorIndex == 0 ? 0 : Integer.parseInt(modifiers.substring(0, seperatorIndex));
					int rangeEnd = seperatorIndex == modifiers.length() - 1 ? params.length : Integer.parseInt(modifiers.substring(seperatorIndex + 1, modifiers.length()));
					if (rangeStart < rangeEnd && rangeStart < params.length)
						s.addReplacement(param, StringUtils.join((String[]) Arrays.copyOfRange(params, rangeStart, (rangeEnd >= params.length ? params.length : rangeEnd) + 1), " "));
				}
			}
		}
		if (sender instanceof Player) {
			Player p = (Player) sender;
			s.addReplacement("<world_name>", p.getWorld().getName());
			VaultHook v = SimpleAlias.getVaultHook();
			if (v.isEnabled()) {
				if (v.isEconomyEnabled())
					s.addReplacement("<money_balance>", Double.toString(v.getBalance(p)));
				if (v.isPermissionEnabled() && v.hasPermissionGroupSupport())
					s.addReplacement("<group_name>", v.getPrimaryGroup(p));
			}
			FactionsHook f = SimpleAlias.getFactionsHook();
			if (f.isEnabled())
				s.addReplacement("<faction_name>", f.getFaction(p));
		}
		return s.applyReplacement(target).replaceAll(REPLACE_REGEX, "");
	}

	@Override
	public int compareTo(Action a) {
		return Integer.compare(priority, a.priority);
	}

	public void setEnabledWorlds(Set<String> enabledWorlds) {
		this.enabledWorlds = enabledWorlds;
	}

	public void setEnabledPermissionNodes(Set<String> enabledPermissionNodes) {
		this.enabledPermissionNodes = enabledPermissionNodes;
	}

	public void setEnabledPermissionGroups(Set<String> enabledPermissionGroups) {
		this.enabledPermissionGroups = enabledPermissionGroups;
	}

	public void setEnabledParams(Map<Integer, String> enabledParams) {
		this.enabledParams = enabledParams;
	}

	public void setPriority(int priority) {
		this.priority = priority;
	}

	public void setTranslateColorCodes(boolean translateColorCodes) {
		this.translateColorCodes = translateColorCodes;
	}

	@Override
	public String getName() {
		return this.name;
	}

	public Set<String> getEnabledWorlds() {
		return enabledWorlds;
	}

	public boolean isEnabled(World w) {
		return enabledWorlds.isEmpty() || enabledWorlds.contains(w.getName());
	}

	public Set<String> getEnabledPermissionNodes() {
		return enabledPermissionNodes;
	}

	public Set<String> getEnabledPermissionGroups() {
		return enabledPermissionGroups;
	}

	public boolean isEnabled(Player p) {
		if (!isEnabled(p.getWorld())) {
			return false;
		} else {
			VaultHook v = SimpleAlias.getVaultHook();
			if (enabledPermissionNodes.size() == 0) {
				return enabledPermissionGroups.size() == 0 ? true : v.isInAnyGroup(p, enabledPermissionGroups);
			} else {
				for (String node : enabledPermissionNodes)
					if (Permission.hasPermission(p, node))
						return enabledPermissionGroups.size() == 0 ? true : v.isInAnyGroup(p, enabledPermissionGroups);
				return false;
			}
		}
	}

	public Map<Integer, String> getEnabledParams() {
		return enabledParams;
	}

	public boolean isEnabled(String[] params) {
		for (Entry<Integer, String> e : enabledParams.entrySet()) {
			int index = e.getKey();
			if (index >= params.length || !params[index].equalsIgnoreCase(e.getValue()))
				return false;
		}
		return true;
	}

	public boolean isEnabled(CommandSender sender, String[] params) {
		return sender instanceof Player ? isEnabled((Player) sender) && isEnabled(params) : isEnabled(params);
	}

	public int getPriority() {
		return this.priority;
	}

	public boolean getTranslateColorCodes() {
		return this.translateColorCodes;
	}

	public abstract Type getType();
}