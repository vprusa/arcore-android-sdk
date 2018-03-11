package com.google.ar.core.examples.java.helloar.rendering;

import com.google.ar.core.Pose;

//http://schabby.de/picking-opengl-ray-tracing/
public class Vector3f
{
	public float x, y, z;

    public Vector3f(){}


	public Vector3f(Pose p){
		this.x = p.tx();
		this.y = p.ty();
		this.z = p.tz();
	}


	public Vector3f(float[] f){
		this.x = f[0];
		this.y = f[1];
		this.z = f[2];
	}

	public Vector3f(float x, float y, float z){
		this.x = x;
		this.y = y;
		this.z = z;
	}

	public Vector3f sum(Vector3f a) {
		return new Vector3f(this.x,this.y, this.z).add(a);
	}

	// Constructors as well as getters/setters omitted for brevity!!
	// Only important methods kept necessary for this tutorial.
	// The original class contains many more methods...
 
	public Vector3f add(Vector3f a) {
		x += a.x;
		y += a.y;
		z += a.z;
 
		return this;
	}
 
	public Vector3f set(Vector3f v)	{
		this.x = v.x;
		this.y = v.y;
		this.z = v.z;
 
		return this;
	}

	public Vector3f mul(float v)	{
		return new Vector3f (this.x * v,this.y * v,this.z * v);
	}

	public Vector3f sub(Vector3f b) {
		float x1 = this.x - b.x;
		float  y2 = this.y - b.y;
		float  z3 = this.z - b.z;

		return sub(new Vector3f(x1,y2,z3));
	}
	public Vector3f sub(Vector3f a, Vector3f b) {
		return subAndAssign(a,b);
	}
	public Vector3f subAndAssign(Vector3f a, Vector3f b) {
		x = a.x - b.x;
		y = a.y - b.y;
		z = a.z - b.z;
 
		return this;
	}
 
	/**
	 * Returns the length of the vector, also called L2-Norm or Euclidean Norm.
	 */
	public float l2Norm() {
		return (float) Math.sqrt(x*x+y*y+z*z);
	}

	public Vector3f cross(Vector3f b) {
		Vector3f a = this;
		float tempX = a.y * b.z - a.z * b.y;
		float tempY = a.z * b.x - a.x * b.z;
		float tempZ = a.x * b.y - a.y * b.x;

		return new Vector3f(tempX, tempY, tempZ);
	}
 
	public Vector3f crossAndAssign(Vector3f a, Vector3f b) {
		float tempX = a.y * b.z - a.z * b.y;
		float tempY = a.z * b.x - a.x * b.z;
		float tempZ = a.x * b.y - a.y * b.x;
 
		x = tempX;
		y = tempY;
		z = tempZ;
 
		return this;
	}
 
	public Vector3f scale(float scalar) {
		x *= scalar;
		y *= scalar;
		z *= scalar;
 
		return this;
	}
 
	public Vector3f normalize() {
		float length = l2Norm();
		x /= length;
		y /= length;
		z /= length;
 
		return this;
	}
}