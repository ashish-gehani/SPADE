package spade.utility;

import spade.core.Graph;

public class ABEGraph extends Graph
{
	private String level;
	private String lowKey;
	private String mediumKey;
	private String highKey;

	public String getLowKey()
	{
		return lowKey;
	}

	public void setLowKey(String lowKey)
	{
		this.lowKey = lowKey;
	}

	public String getMediumKey()
	{
		return mediumKey;
	}

	public void setMediumKey(String mediumKey)
	{
		this.mediumKey = mediumKey;
	}

	public String getHighKey()
	{
		return highKey;
	}

	public void setHighKey(String highKey)
	{
		this.highKey = highKey;
	}

	public void setLevel(String level)
	{
		this.level = level;
	}

	public String getLevel()
	{
		return level;
	}
}
