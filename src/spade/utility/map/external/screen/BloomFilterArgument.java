/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2019 SRI International

 This program is free software: you can redistribute it and/or
 modify it under the terms of the GNU General Public License as
 published by the Free Software Foundation, either version 3 of the
 License, or (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program. If not, see <http://www.gnu.org/licenses/>.
 --------------------------------------------------------------------------------
 */
package spade.utility.map.external.screen;

/**
 * Bloom filter screen argument
 */
public abstract class BloomFilterArgument extends ScreenArgument{
	
	public final static String keyExpectedElements = "expectedElements",
			keyFalsePositiveProbability = "falsePositiveProbability",
			keySavePath = "loadPath",
			keyLoadPath = "savePath";
	/**
	 * Path to write the bloom filter object to at close
	 */
	public final String savePath;
	
	protected BloomFilterArgument(String savePath){
		super(ScreenName.BloomFilter);
		this.savePath = savePath;
	}
	
	@Override
	public int hashCode(){
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + ((savePath == null) ? 0 : savePath.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj){
		if(this == obj)
			return true;
		if(!super.equals(obj))
			return false;
		if(getClass() != obj.getClass())
			return false;
		BloomFilterArgument other = (BloomFilterArgument)obj;
		if(savePath == null){
			if(other.savePath != null)
				return false;
		}else if(!savePath.equals(other.savePath))
			return false;
		return true;
	}

	@Override
	public String toString(){
		return "BloomFilterArgument [savePath=" + savePath + "]";
	}

	protected static class LoadFromFile extends BloomFilterArgument{
		
		public final String loadPath;
		
		protected LoadFromFile(String loadPath, String savePath){
			super(savePath);
			this.loadPath = loadPath;
		}
		
		@Override
		public String toString(){
			return "LoadFromFile [loadPath=" + loadPath + ", savePath=" + savePath + ", name=" + name + "]";
		}

		@Override
		public int hashCode(){
			final int prime = 31;
			int result = super.hashCode();
			result = prime * result + ((loadPath == null) ? 0 : loadPath.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj){
			if(this == obj)
				return true;
			if(!super.equals(obj))
				return false;
			if(getClass() != obj.getClass())
				return false;
			LoadFromFile other = (LoadFromFile)obj;
			if(loadPath == null){
				if(other.loadPath != null)
					return false;
			}else if(!loadPath.equals(other.loadPath))
				return false;
			return true;
		}
	}
	
	protected static class CreateFromArgs extends BloomFilterArgument{
		
		public final int expectedElements;
		public final double falsePositiveProbability;
		
		protected CreateFromArgs(int expectedElements, double falsePositiveProbability, String savePath){
			super(savePath);
			this.expectedElements = expectedElements;
			this.falsePositiveProbability = falsePositiveProbability;
		}
		
		@Override
		public String toString(){
			return "CreateFromArgs [expectedElements=" + expectedElements + ", falsePositiveProbability="
					+ falsePositiveProbability + "]";
		}
		
		@Override
		public int hashCode(){
			final int prime = 31;
			int result = super.hashCode();
			result = prime * result + expectedElements;
			long temp;
			temp = Double.doubleToLongBits(falsePositiveProbability);
			result = prime * result + (int)(temp ^ (temp >>> 32));
			return result;
		}
		
		@Override
		public boolean equals(Object obj){
			if(this == obj)
				return true;
			if(!super.equals(obj))
				return false;
			if(getClass() != obj.getClass())
				return false;
			CreateFromArgs other = (CreateFromArgs)obj;
			if(expectedElements != other.expectedElements)
				return false;
			if(Double.doubleToLongBits(falsePositiveProbability) != Double
					.doubleToLongBits(other.falsePositiveProbability))
				return false;
			return true;
		}
		
	}
}