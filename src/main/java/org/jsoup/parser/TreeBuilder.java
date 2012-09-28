package org.jsoup.parser;

import org.jsoup.helper.DescendableLinkedList;
import org.jsoup.helper.Validate;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.concurrent.*;


/**
 * @author Jonathan Hedley
 */
abstract class TreeBuilder {
    CharacterReader reader;
    Tokeniser tokeniser;
    protected Document doc; // current doc we are building into
    protected DescendableLinkedList<Element> stack; // the stack of open elements
    protected String baseUri; // current base uri, for creating new elements
    protected Token currentToken; // currentToken is used only for error tracking.
    protected ParseErrorList errors; // null when not tracking errors

    private ConcurrentLinkedQueue<Token> queue;
    private volatile boolean parserExit;
    private VFlag vFlag;		// to indicate a dependence validation occurred (true) or not (false).
    private Token currToken;	// the current token that just is being processed by tree builder.
    
    protected void initialiseParse(String input, String baseUri, ParseErrorList errors) {
        Validate.notNull(input, "String input must not be null");
        Validate.notNull(baseUri, "BaseURI must not be null");
        
        doc = new Document(baseUri);
        reader = new CharacterReader(input);
        this.errors = errors;
        tokeniser = new Tokeniser(reader, errors);
        stack = new DescendableLinkedList<Element>();
        this.baseUri = baseUri;
        
        queue = new ConcurrentLinkedQueue<Token>();
        parserExit = false;
        vFlag = new VFlag(false);
        currToken = null;
    }

    Document parse(String input, String baseUri) {
        return parse(input, baseUri, ParseErrorList.noTracking());
    }

    Document parse(String input, String baseUri, ParseErrorList errors) {
    	initialiseParse(input, baseUri, errors);
        runParser();
        return doc;
    }

    static float total;
    static int count;
    static float ave;

    protected void runParser() {        
        Thread tokenizerThread = new Thread( new TokenizerThread());
        Thread parserThread = new Thread( new ParserThread());
        tokenizerThread.start();
        parserThread.start();
        try {
			tokenizerThread.join();
	        parserThread.join();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
    }

    protected abstract boolean process(Token token, VFlag vFlag);

    protected Element currentElement() {
        return stack.getLast();
    }
    
    private class TokenizerThread implements Runnable
    {  	
    	public void run()
    	{
    		long sta = 0;
    		long mid = 0;
    		long end = 0;
    		long tokenizationTime = 0;
    		long addTime = 0;
            while (true) {
            	
            	if (vFlag.value == true)
            	{
            		queue.clear();
            		//System.out.println("rollback from pos "+tokeniser.getPos()+" to pos "+currToken.index);
            		tokeniser.setStatus(currToken);
            		//System.out.println("correct state: "+currToken.endState);
            		vFlag.value = false;
            	}
            	Token token = null;
            	if(queue.size() < 1)
            	{
            		//sta = System.nanoTime();
	            	token = tokeniser.read();
	            	//mid = System.nanoTime();
	    			//System.out.println("TokenType     : "+token.type+"\t token: "+token);
	    			//System.out.println("TokenizerState: "+tokeniser.getState());
					queue.add(token);
					//end = System.nanoTime();
					//tokenizationTime += mid - sta;
					//addTime += end - mid;
            	}
                if (token !=null && token.type == Token.TokenType.EOF)
                	break;
            }
            
            parserExit = true;
            //System.out.println("tokenization time:       "+tokenizationTime);
            //System.out.println("add queue time:          "+addTime);
    	}
    }
    
    private class ParserThread implements Runnable
    {
    	
    	public void run()
    	{
    		long sta = 0;
    		long end = 0;
    		long stal = 0;
    		long endl = 0;
    		long treebuildingTimel = 0;
    		long treebuildingTime = 0;
    		long pollTime = 0;
    		sta = System.nanoTime();
            
    		while(parserExit == false)
    		{
    			Token token = null;
    			if(vFlag.value == false)
    			{
    				//stal = System.nanoTime();
    				token = queue.poll();
    				//endl = System.nanoTime();
    				//pollTime += endl - stal;
    			}
    			if(token != null)
    			{    			
    				currToken = token;
    				//System.out.println("token: "+token);
    				//stal = System.nanoTime();
    				process(token, vFlag);
    				//endl = System.nanoTime();
    				//treebuildingTimel += endl - stal;
    			}
    		}
 //   		out.close();
    		//System.out.println("parser thread exited.");
    		//end = System.nanoTime();
    		//treebuildingTime = end - sta;
    		//System.out.println("treebuilding time total: "+treebuildingTime);
    		//System.out.println("queue poll time accum:   "+pollTime);
    		//System.out.println("treebuilding time accum: "+treebuildingTimel);
    	}
    }
    
}

class VFlag
{
	volatile boolean value;
	VFlag(boolean value)
	{
		this.value = value;
	}
}