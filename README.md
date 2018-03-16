#  Exploring Data Types - Java BTree
## CS340

## Author(s):

Lucas Rappette

## Date:

3/16/18


## Description:

A BTree implementation written in Java. There are many test drivers in which data is inserted into BTrees that are dynamically created.
Also supports reading from a DBTable file and constructing a BTree from the Table

## How to build the software

Add this project to any Java IDE, it will automatically compile.

If this does not work execute the command below on the command line to build the BTree implementation.

```
javac -d bin -sourcepath src src/*
```

Afterwards, execute the following on the commandline to build the BTree drivers.

```
javac -d bin -sourcepath src src/drivers/*
```


## How to use the software

Execute the command below on a command line in the directory, or run from the 
IDE with runtime arguments.

```
java -cp bin; x
```

_Valid Arguments:_

- The first arg is required, __x__ is the desired demo driver to run.
	- Options are: h6a, h6b, Driver, DemoDriver

## How the software was tested

Testing was completed by using random inserts into the BTree. This project is very old so I'm not sure what other testing I did.


## Known bugs and problem areas

None.