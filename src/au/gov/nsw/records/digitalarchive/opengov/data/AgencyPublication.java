package au.gov.nsw.records.digitalarchive.opengov.data;

// Generated Aug 29, 2012 1:21:11 PM by Hibernate Tools 3.4.0.CR1

/**
 * AgencyPublication generated by hbm2java
 */
public class AgencyPublication implements java.io.Serializable {

	private Integer agencyPublicationId;
	private Publication publication;
	private FullAgencyList fullAgencyList;

	public AgencyPublication() {
	}

	public AgencyPublication(Publication publication,
			FullAgencyList fullAgencyList) {
		this.publication = publication;
		this.fullAgencyList = fullAgencyList;
	}

	public Integer getAgencyPublicationId() {
		return this.agencyPublicationId;
	}

	public void setAgencyPublicationId(Integer agencyPublicationId) {
		this.agencyPublicationId = agencyPublicationId;
	}

	public Publication getPublication() {
		return this.publication;
	}

	public void setPublication(Publication publication) {
		this.publication = publication;
	}

	public FullAgencyList getFullAgencyList() {
		return this.fullAgencyList;
	}

	public void setFullAgencyList(FullAgencyList fullAgencyList) {
		this.fullAgencyList = fullAgencyList;
	}

}
