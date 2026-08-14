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
package spade.utility;

public class DoublyLinkedList<T>{

	private int size;
	private final DoublyLinkedListNode<T> head, tail; // Keep final
	
	public DoublyLinkedList(){
		size = 0;
		head = new DoublyLinkedListNode<T>(null);
		tail = new DoublyLinkedListNode<T>(null);
		head.previous = tail.next = null;
		head.next = tail;
		tail.previous = head;
	}
	
	public void clear(){
		DoublyLinkedListNode<T> current = head.next;
		while(current != tail){
			DoublyLinkedListNode<T> nextCurrent = current.next;
			current.next = current.previous = null;
			current = nextCurrent;
		}
		head.next = tail;
		tail.previous = head;
		size = 0;
	}
	
	private void _emptyAdd(DoublyLinkedListNode<T> node){
		head.next = node;
		node.previous = head;
		tail.previous = node;
		node.next = tail;
	}
	
	public DoublyLinkedListNode<T> addFirst(T t){
		DoublyLinkedListNode<T> node = createNode(t);
		switch(size){
			case 0: _emptyAdd(node); break;
			default:{
				_makeFirst(node);
			}
			break;
		}
		size++;
		return node;
	}
	
	public DoublyLinkedListNode<T> addLast(T t){
		DoublyLinkedListNode<T> node = createNode(t);
		switch(size){
			case 0: _emptyAdd(node); break;
			default:{
				node.next = tail;
				node.previous = tail.previous;
				tail.previous.next = node;
				tail.previous = node;
			}
			break;
		}
		size++;
		return node;
	}
	
	public DoublyLinkedListNode<T> getFirst(){
		if(size == 0){
			return null;
		}else{
			return head.next;
		}
	}
	
	public DoublyLinkedListNode<T> getLast(){
		if(size == 0){
			return null;
		}else{
			return tail.previous;
		}
	}
	
	public DoublyLinkedListNode<T> getNext(final DoublyLinkedListNode<T> node){
		if(node == null){
			return null;
		}else{
			if(node.next == tail){
				return null;
			}else{
				return node.next;
			}
		}
	}
	
	public DoublyLinkedListNode<T> getPrevious(final DoublyLinkedListNode<T> node){
		if(node == null){
			return null;
		}else{
			if(node.previous == head){
				return null;
			}else{
				return node.previous;
			}
		}
	}
	
	private void _makeFirst(DoublyLinkedListNode<T> node){
		node.previous = head;
		node.next = head.next;
		head.next.previous = node;
		head.next = node;
	}
	
	public void makeFirst(DoublyLinkedListNode<T> node){
		if(node != null){
			_unlinkNodeNullChecks(node);
			_makeFirst(node);
		}
	}
	
	private void _makeLast(DoublyLinkedListNode<T> node){
		node.next = tail;
		node.previous = tail.previous;
		tail.previous.next = node;
		tail.previous = node;
	}
	
	public void makeLast(DoublyLinkedListNode<T> node){
		if(node != null){
			_unlinkNodeNullChecks(node);
			_makeLast(node);
		}
	}
	
	private void _unlinkNodeNullChecks(DoublyLinkedListNode<T> node){
		if(node.previous != null){
			node.previous.next = node.next;
		}
		if(node.next != null){
			node.next.previous = node.previous;
		}
		if(node.next != null){
			node.next = null;
		}
		if(node.previous != null){
			node.previous = null;
		}
	}
	
	private void _unlinkNode(DoublyLinkedListNode<T> node){
		node.previous.next = node.next;
		node.next.previous = node.previous;
		node.next = null;
		node.previous = null;
	}
	
	public void removeNode(DoublyLinkedListNode<T> node){
		if(node != null){
			_unlinkNodeNullChecks(node);
			size--;
		}
	}
	
	public DoublyLinkedListNode<T> removeFirst(){
		switch(size){
			case 0: return null;
			default:{
				size--;
				DoublyLinkedListNode<T> node = head.next;
				_unlinkNode(node);
				return node;
			}
		}
	}
	
	public DoublyLinkedListNode<T> removeLast(){
		switch(size){
			case 0: return null;
			default:{
				size--;
				DoublyLinkedListNode<T> node = tail.previous;
				_unlinkNode(node);
				return node;
			}
		}
	}
	
	public String toString(){
		switch(size){
			case 0: return "List(0) []";
			default:{
				String str = "List("+size+") [";
				DoublyLinkedListNode<T> current = head.next;
				while(current != tail){ //
					str += current + ", ";
					current = current.next;
				}
				str = str.substring(0, str.length() - 2);
				str += "]";
				return str;
			}
		}
	}
	
	public int size(){
		return size;
	}
	
	private DoublyLinkedListNode<T> createNode(T t){
		return new DoublyLinkedListNode<T>(t);
	}
	
	public class DoublyLinkedListNode<U>{
		
		private U value;
		private DoublyLinkedListNode<U> previous = null, next = null; // not exposed
		
		private DoublyLinkedListNode(U value){
			this.value = value;
		}
		
		public U getValue(){
			return value;
		}
		
		public String toString(){
			return String.valueOf(value);
		}
	}
}
