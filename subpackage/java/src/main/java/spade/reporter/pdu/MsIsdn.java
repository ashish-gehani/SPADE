package spade.reporter.pdu;

public class MsIsdn
{
	public enum Type
	{
		National, International, Text, Void
	}

	String number = null;

	Type type = Type.International;

	public MsIsdn()
	{
		this("", Type.Void);
	}

	public MsIsdn(String number)
	{
		if (number.length() > 0 && number.charAt(0) == '+')
		{
			this.number = number.substring(1);
			this.type = Type.International;
		}
		else
		{
			this.number = number;
			this.type = typeOf(number);
		}
	}

	public MsIsdn(String number, Type type)
	{
		this.number = number;
		this.type = type;
	}

	public MsIsdn(MsIsdn msisdn)
	{
		this.type = msisdn.getType();
		this.number = msisdn.getNumber();
	}

	public String getNumber()
	{
		return this.number;
	}

	public Type getType()
	{
		return this.type;
	}

	public boolean isVoid()
	{
		return (this.type == Type.Void);
	}

	@Override
	public boolean equals(Object o)
	{
		if (this == o) return true;
		if (!(o instanceof MsIsdn)) return false;
		return (this.number.equalsIgnoreCase(((MsIsdn) o).getNumber()));
	}

	@Override
	public String toString()
	{
		return String.format("[%s / %s]", getType(), getNumber());
	}

	@Override
	public int hashCode()
	{
		return number.hashCode() + (15 * type.hashCode());
	}
	
	public static boolean isNullOrEmpty(String s)
	{
		return ((s == null) || (s.trim().length() == 0));
	}
	
	private static Type typeOf(String number)
	{
		if (isNullOrEmpty(number)) return Type.Void;
		for (int i = 0; i < number.length(); i++)
		{
			if (!Character.isDigit(number.charAt(i))) return Type.Text;
		}
		return Type.International;
	}
}
