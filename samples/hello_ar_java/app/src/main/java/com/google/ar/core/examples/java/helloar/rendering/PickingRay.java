package com.google.ar.core.examples.java.helloar.rendering;

import com.google.ar.core.Camera;
//http://schabby.de/picking-opengl-ray-tracing/
public class PickingRay
{
	private Vector3f clickPosInWorld = new Vector3f();
	private Vector3f direction = new Vector3f();

	public PickingRay(Vector3f clickPosInWorld,Vector3f direction ){
		this.clickPosInWorld = clickPosInWorld;
		this.direction = direction;
	}

	/**
	 * Computes the intersection of this ray with the X-Y Plane (where Z = 0)
	 * and writes it back to the provided vector.
	 */
	public void intersectionWithXyPlane(float[] worldPos)
	{
		float s = -clickPosInWorld.z / direction.z;
		worldPos[0] = clickPosInWorld.x+direction.x*s;
		worldPos[1] = clickPosInWorld.y+direction.y*s;
		worldPos[2] = 0;
	}
 
	public Vector3f getClickPosInWorld() {
		return clickPosInWorld;
	}
	public Vector3f getDirection() {
		return direction;
	}
/*
	static public void lookAt(Camera camera, PickingRay pickingRay){

		//Vector3f position = new Vector3f(camera.getPose().tx(), camera.getPose().ty(), camera.getPose().tz());
		//Vector3f view = new Vector3f((float)View.getLocationOnScreen()[0], (float)View.getLocationOnScreen()[1], 0.0f);

		float[] viewMatrix= new float[16];

		float[] viewPort= new float[16];

		camera.getViewMatrix(viewMatrix,0);

		Vector3f view  = new Vector3f(viewMatrix[ 2 ],viewMatrix[ 6 ],viewMatrix[ 10 ]);
		float[] positionMatrix= new float[16];

		//camera.getDisplayOrientedPose().ViewMatrix(viewMatrix,0);
		Vector3f position  = new Vector3f(camera.getDisplayOrientedPose().tx(),camera.getDisplayOrientedPose().ty(),camera.getDisplayOrientedPose().tz());

		pickingRay.getClickPosInWorld().set(position);
		pickingRay.getClickPosInWorld().add(view);
		float viewportHeight = viewPort[2];
		float viewportWidth = viewPort[2];


		// look direction
		view.subAndAssign(lookAt, position).normalize();

		// screenX
		screenHoritzontally.crossAndAssign(view, up).normalize();

		// screenY
		screenVertically.crossAndAssign(screenHoritzontally, view).normalize();

		final float radians = (float) (viewAngle*Math.PI / 180f);
		float halfHeight = (float) (Math.tan(radians/2)*nearClippingPlaneDistance);
		float halfScaledAspectRatio = halfHeight*getViewportAspectRatio();

		screenVertically.scale(halfHeight);
		screenHoritzontally.scale(halfScaledAspectRatio);

	}*/
}