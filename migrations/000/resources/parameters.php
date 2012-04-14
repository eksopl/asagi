<?php 

if(PHP_SAPI != 'cli')
{
	die('This file must be accessed via command line.');
}

/********************************************** 
 * Simple argv[] parser for CLI scripts 
 * Diego Mendes Rodrigues - S�o Paulo - Brazil 
 * diego.m.rodrigues [at] gmail [dot] com 
 * May/2005 
 **********************************************/ 

class arg_parser { 
     var $argc; 
     var $argv; 
     var $parsed; 
     var $force_this; 

     function arg_parser($force_this="") { 
         global $argc, $argv; 
         $this->argc = $argc; 
         $this->argv = $argv; 
         $this->parsed = array(); 
         
         array_push($this->parsed, 
                            array($this->argv[0]) ); 

         if ( !empty($force_this) ) 
             if ( is_array($force_this) ) 
                 $this->force_this = $force_this; 

         //Sending parameters to $parsed 
         if ( $this->argc > 1 ) { 
             for($i=1 ; $i< $this->argc ; $i++) { 
                 //We only have passed -xxxx 
                 if ( substr($this->argv[$i],0,1) == "-" ) { 
                     //Se temos -xxxx xxxx 
                     if ( $this->argc > ($i+1) ) { 
                         if ( substr($this->argv[$i+1],0,1) != "-" ) { 
                             array_push($this->parsed, 
                                 array($this->argv[$i], 
                                     $this->argv[$i+1]) ); 
                             $i++; 
                             continue; 
                         } 
                     } 
                 } 
                 //We have passed -xxxxx1 xxxxx2 
                 array_push($this->parsed, 
                                                   array($this->argv[$i]) ); 
             } 
         } 

                 //Testing if all necessary parameters have been passed 
                 $this->force(); 
     } 

     //Testing if one parameter have benn passed 
     function passed($argumento) { 
         for($i=0 ; $i< $this->argc ; $i++) 
             if ( isset($this->parsed[$i][0]) && $this->parsed[$i][0] == $argumento ) 
                 return $i; 
         return 0; 
     } 

     //Testing if you have passed a estra argument, -xxxx1 xxxxx2 
     function full_passed($argumento) { 
         $findArg = $this->passed($argumento); 
         if ( $findArg ) 
             if ( count($this->parsed[$findArg] ) > 1 ) 
                 return $findArg; 
         return 0; 
     } 

         //Returns  xxxxx2 at a " -xxxx1 xxxxx2" call 
     function get_full_passed($argumento) { 
                 $findArg = $this->full_passed($argumento); 

                 if ( $findArg ) 
                     return $this->parsed[$findArg][1]; 

                 return; 
         } 
     
     //Necessary parameters to script 
     function force() { 
         if ( is_array( $this->force_this ) ) { 
             for($i=0 ; $i< count($this->force_this) ; $i++) { 
                 if ( $this->force_this[$i][1] == "SIMPLE" 
                      && !$this->passed($this->force_this[$i][0]) 
                 ) 
                     die("\n\nMissing " . $this->force_this[$i][0] . "\n\n"); 

                                 if ( $this->force_this[$i][1] == "FULL" 
                                      && !$this->full_passed($this->force_this[$i][0]) 
                 ) 
                                         die("\n\nMissing " . $this->force_this[$i][0] ." <arg>\n\n"); 
             } 
         } 
     } 
 } 
/*
//Example 
$forcar = array( 
         array("-name", "FULL"), 
         array("-email","SIMPLE") ); 

$parser = new arg_parser($forcar); 

 if ( $parser->passed("-show") ) 
     echo "\nGoing...:"; 

 echo "\nName: " . $parser->get_full_passed("-name"); 

 if ( $parser->full_passed("-email") )  
     echo "\nEmail: " . $parser->get_full_passed("-email"); 
 else 
         echo "\nEmail: default"; 

 if ( $parser->full_passed("-copy") ) 
         echo "\nCopy To: " . $parser->get_full_passed("-copy"); 

 echo "\n\n"; */
?>